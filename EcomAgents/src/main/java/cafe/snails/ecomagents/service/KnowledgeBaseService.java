package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.KnowledgeAuditLogRepository;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import cafe.snails.ecomagents.service.rag.KnowledgeUnit;
import cafe.snails.ecomagents.service.rag.KnowledgeUnitParserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 知识库业务逻辑，包括知识库 CRUD、文档管理、全文搜索、RAG 上下文构建和审计日志。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]{3,}");

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeDocumentRepository docRepository;
    private final KnowledgeAuditLogRepository auditLogRepository;
    private final AgentRepository agentRepository;
    private final WorkspaceInitService workspaceInitService;
    private final LocalKnowledgeIndexService localKnowledgeIndexService;
    private final LlmConfig llmConfig;
    private final KnowledgeUnitParserService knowledgeUnitParserService;

    // ========== Knowledge Base CRUD ==========

    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return ApiResponse.success(kbRepository.findAll());
    }

    public ApiResponse<KnowledgeBase> getKnowledgeBase(Long id) {
        return kbRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "知识库不存在"));
    }

    public ApiResponse<KnowledgeBase> createKnowledgeBase(KnowledgeBase kb, Long userId) {
        kb.setId(null);
        kb.setCreatedAt(LocalDate.now());
        kb.setCreatedBy(userId);
        return ApiResponse.success("知识库创建成功", kbRepository.save(kb));
    }

    public ApiResponse<KnowledgeBase> updateKnowledgeBase(Long id, KnowledgeBase updates) {
        return kbRepository.findById(id)
                .map(kb -> {
                    if (updates.getName() != null) kb.setName(updates.getName());
                    if (updates.getDescription() != null) kb.setDescription(updates.getDescription());
                    return ApiResponse.success("知识库更新成功", kbRepository.save(kb));
                })
                .orElseGet(() -> ApiResponse.error(404, "知识库不存在"));
    }

    @Transactional
    public ApiResponse<Void> deleteKnowledgeBase(Long id) {
        if (!kbRepository.existsById(id)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        List<Agent> agents = agentRepository.findByKnowledgeBaseId(id);
        for (Agent agent : agents) {
            List<Long> remainingKbIds = agent.getKnowledgeBaseIds() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(agent.getKnowledgeBaseIds());
            remainingKbIds.removeIf(kbId -> id.equals(kbId));
            agent.setKnowledgeBaseIds(remainingKbIds);
            agentRepository.save(agent);
            workspaceInitService.updateKnowledgeMd(agent.getId(), null);
        }

        localKnowledgeIndexService.evict(id);
        List<KnowledgeDocument> docs = docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(id);
        docRepository.deleteAll(docs);
        kbRepository.deleteById(id);
        return ApiResponse.success("知识库已删除", null);
    }

    // ========== Document Management ==========

    public ApiResponse<List<KnowledgeDocument>> listDocuments(Long kbId) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        return ApiResponse.success(docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(kbId));
    }

    @Transactional
    public ApiResponse<KnowledgeDocument> uploadDocument(Long kbId, MultipartFile file, Long userId,
                                                         String username, HttpServletRequest request) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            return ApiResponse.error(400, "文件名不能为空");
        }

        String ext = getExtension(fileName).toLowerCase();
        String content;
        try {
            content = extractText(file, ext);
        } catch (Exception e) {
            log.error("Failed to extract text from {}: {}", fileName, e.getMessage());
            return ApiResponse.error(500, "文件解析失败: " + e.getMessage());
        }

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .knowledgeBaseId(kbId)
                .fileName(fileName)
                .fileType(ext)
                .content(content)
                .charCount(content.length())
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(userId)
                .build();

        KnowledgeDocument saved = docRepository.save(doc);

        localKnowledgeIndexService.rebuildAsync(kbId);

        // Sync to agent workspaces
        syncKnowledgeBaseToAgents(kbId);

        // Audit log
        writeAuditLog(kbId, userId, username, "UPLOAD", fileName, request);

        return ApiResponse.success("文档上传成功", saved);
    }

    @Transactional
    public ApiResponse<Void> deleteDocument(Long kbId, Long docId, Long userId,
                                            String username, HttpServletRequest request) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        var docOpt = docRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ApiResponse.error(404, "文档不存在");
        }
        KnowledgeDocument doc = docOpt.get();

        docRepository.deleteById(docId);
        localKnowledgeIndexService.rebuildAsync(kbId);
        syncKnowledgeBaseToAgents(kbId);

        // Audit log
        writeAuditLog(kbId, userId, username, "DELETE", doc.getFileName(), request);

        return ApiResponse.success("文档已删除", null);
    }

    // ========== Search ==========

    public ApiResponse<List<KnowledgeDocument>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(docRepository.searchByKeyword(keyword.trim()));
    }

    public ApiResponse<List<KnowledgeDocument>> searchInKbs(String keyword, List<Long> kbIds) {
        if (keyword == null || keyword.isBlank() || kbIds == null || kbIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(docRepository.searchByKeywordAndKbIds(keyword.trim(), kbIds));
    }

    // ========== Chat Integration ==========

    public String buildKnowledgeContext(List<Long> kbIds, String userQuery) {
        if (kbIds == null || kbIds.isEmpty()) return "";

        long startedAt = System.nanoTime();
        LocalKnowledgeIndexService.KnowledgeSearchResult vectorResult = localKnowledgeIndexService.searchSimilarDetailed(
                kbIds,
                userQuery,
                llmConfig.getRagSearchLimit(),
                llmConfig.getRagSimilarityThreshold()
        );
        List<String> chunks = vectorResult == null ? List.of() : vectorResult.chunks();
        if (chunks != null && !chunks.isEmpty()) {
            StringBuilder context = new StringBuilder("\n\nKnowledge retrieval status: ")
                    .append(vectorResult.degraded() ? "degraded_vector_search" : "vector_search")
                    .append(vectorResult.timedOut() ? " (timeout fallback available)" : "")
                    .append("\n\nUse the knowledge context below to answer. Do not call web_search or local file tools unless the user explicitly asks for realtime, external, or workspace file information.\n\n");
            for (int i = 0; i < chunks.size(); i++) {
                context.append("--- chunk ").append(i + 1).append(" ---\n")
                        .append(truncate(chunks.get(i), 3000))
                        .append("\n\n");
            }
            String limited = limitContext(context.toString());
            log.info("Knowledge context built from vector search: kbCount={}, chunks={}, degraded={}, timedOut={}, elapsedMs={}, returnedChars={}",
                    kbIds.size(), chunks.size(), vectorResult.degraded(), vectorResult.timedOut(),
                    elapsedMillis(startedAt), limited.length());
            return limited;
        }

        List<KnowledgeUnit> units = findRelevantUnits(kbIds, userQuery);
        if (units.isEmpty()) {
            log.info("Knowledge context empty: kbCount={}, degraded={}, timedOut={}, elapsedMs={}",
                    kbIds.size(), vectorResult != null && vectorResult.degraded(),
                    vectorResult != null && vectorResult.timedOut(), elapsedMillis(startedAt));
            if (vectorResult != null && vectorResult.timedOut()) {
                return "Knowledge retrieval status: vector_timeout_no_fallback\n\nNo relevant knowledge context is available.";
            }
            return "";
        }

        String status = vectorResult != null && vectorResult.timedOut()
                ? "text_search_fallback_after_vector_timeout"
                : "text_search_fallback";
        StringBuilder context = new StringBuilder("\n\nKnowledge retrieval status: ")
                .append(status)
                .append("\n\nUse the knowledge context below to answer. Do not call web_search or local file tools unless the user explicitly asks for realtime, external, or workspace file information.\n\n");
        for (int i = 0; i < Math.min(units.size(), llmConfig.getRagSearchLimit()); i++) {
            KnowledgeUnit unit = units.get(i);
            context.append("--- ").append(unit.fileName());
            if (unit.sourceLocation() != null && !unit.sourceLocation().isBlank()) {
                context.append(" @ ").append(unit.sourceLocation());
            }
            context.append(" ---\n")
                    .append(buildRelevantSnippet(unit, userQuery, 1800)).append("\n\n");
        }
        String limited = limitContext(context.toString());
        log.info("Knowledge context built from text fallback: kbCount={}, units={}, degraded={}, timedOut={}, elapsedMs={}, returnedChars={}",
                kbIds.size(), units.size(), vectorResult != null && vectorResult.degraded(),
                vectorResult != null && vectorResult.timedOut(), elapsedMillis(startedAt), limited.length());
        return limited;
    }

    private List<KnowledgeUnit> findRelevantUnits(List<Long> kbIds, String userQuery) {
        String keyword = userQuery == null ? "" : userQuery.trim();
        List<KnowledgeDocument> docs = List.of();
        if (!keyword.isBlank()) {
            docs = docRepository.searchByKeywordAndKbIds(keyword, kbIds);
        }
        if (docs == null || docs.isEmpty()) {
            docs = docRepository.findByKnowledgeBaseIdIn(kbIds);
        }
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        Set<String> terms = extractSearchTerms(keyword);
        Set<String> identifiers = extractIdentifierTerms(keyword);
        List<KnowledgeUnit> parsedUnits = docs.stream()
                .flatMap(doc -> knowledgeUnitParserService.parse(doc).stream())
                .toList();
        List<ScoredUnit> scoredUnits = parsedUnits.stream()
                .map(unit -> new ScoredUnit(unit, scoreUnit(unit, terms, identifiers)))
                .filter(scored -> scored.score() > 0 || terms.isEmpty())
                .sorted(Comparator.comparingInt(ScoredUnit::score).reversed())
                .toList();
        if (scoredUnits.isEmpty()) {
            return List.of();
        }
        int topScore = scoredUnits.get(0).score();
        int minScore = topScore > 0 ? Math.max(1, (int) Math.ceil(topScore * 0.75)) : 0;
        List<ScoredUnit> selected = scoredUnits.stream()
                .filter(scored -> scored.score() >= minScore)
                .limit(Math.max(1, llmConfig.getRagSearchLimit()))
                .toList();
        log.info("Knowledge unit retrieval candidates: docs={}, parsedUnits={}, matchedUnits={}, selectedUnits={}, topScore={}, minScore={}, identifiers={}",
                docs.size(), parsedUnits.size(), scoredUnits.size(), selected.size(), topScore, minScore, identifiers);
        for (int i = 0; i < Math.min(selected.size(), 5); i++) {
            ScoredUnit scored = selected.get(i);
            KnowledgeUnit unit = scored.unit();
            log.info("Knowledge unit selected: rank={}, score={}, docId={}, fileName={}, fileType={}, unitType={}, source={}, title={}, chars={}",
                    i + 1, scored.score(), unit.documentId(), unit.fileName(), unit.fileType(), unit.unitType(),
                    unit.sourceLocation(), safeLog(unit.title()), unit.content() == null ? 0 : unit.content().length());
        }
        return selected.stream()
                .map(ScoredUnit::unit)
                .toList();
    }

    private List<KnowledgeDocument> findRelevantDocuments(List<Long> kbIds, String userQuery) {
        String keyword = userQuery == null ? "" : userQuery.trim();
        if (!keyword.isBlank()) {
            List<KnowledgeDocument> exactMatches = docRepository.searchByKeywordAndKbIds(keyword, kbIds);
            if (!exactMatches.isEmpty()) {
                return exactMatches;
            }
        }

        List<KnowledgeDocument> docs = docRepository.findByKnowledgeBaseIdIn(kbIds);
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        Set<String> terms = extractSearchTerms(keyword);
        if (terms.isEmpty()) {
            return docs.stream()
                    .filter(doc -> doc.getContent() != null && !doc.getContent().isBlank())
                    .limit(5)
                    .toList();
        }

        return docs.stream()
                .filter(doc -> scoreDocument(doc, terms) > 0)
                .sorted(Comparator.comparingInt((KnowledgeDocument doc) -> scoreDocument(doc, terms)).reversed())
                .limit(5)
                .toList();
    }

    private Set<String> extractSearchTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        for (String term : normalized.split("\\s+")) {
            if (term.length() >= 2 && term.length() <= 80) {
                terms.add(term);
            }
            addCjkNgrams(terms, term);
        }
        String compact = normalized.replace(" ", "");
        if (compact.length() >= 2 && compact.length() <= 80) {
            terms.add(compact);
        }
        addCjkNgrams(terms, compact);
        return terms;
    }

    private Set<String> extractIdentifierTerms(String query) {
        Set<String> identifiers = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return identifiers;
        }
        var matcher = IDENTIFIER_PATTERN.matcher(query);
        while (matcher.find()) {
            identifiers.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return identifiers;
    }

    private void addCjkNgrams(Set<String> terms, String text) {
        if (text == null || text.length() < 2 || !containsCjk(text)) {
            return;
        }
        int maxTerms = 80;
        for (int n = 4; n >= 2; n--) {
            if (text.length() < n) {
                continue;
            }
            for (int i = 0; i <= text.length() - n && terms.size() < maxTerms; i++) {
                String gram = text.substring(i, i + n);
                if (containsCjk(gram)) {
                    terms.add(gram);
                }
            }
        }
    }

    private boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
        }
        return false;
    }

    private int scoreDocument(KnowledgeDocument doc, Set<String> terms) {
        if (doc.getContent() == null || doc.getContent().isBlank()) {
            return 0;
        }
        String text = doc.getContent().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                score += term.length();
            }
        }
        return score;
    }

    private int scoreUnit(KnowledgeUnit unit, Set<String> terms, Set<String> identifiers) {
        if (unit == null || unit.content() == null || unit.content().isBlank()) {
            return 0;
        }
        String haystack = String.join("\n",
                unit.title() == null ? "" : unit.title(),
                unit.fileName() == null ? "" : unit.fileName(),
                unit.sourceLocation() == null ? "" : unit.sourceLocation(),
                unit.content()
        ).toLowerCase(Locale.ROOT);
        boolean identifierQuery = identifiers != null && !identifiers.isEmpty();
        boolean identifierMatched = false;
        if (identifierQuery) {
            for (String identifier : identifiers) {
                if (haystack.contains(identifier)) {
                    identifierMatched = true;
                    break;
                }
            }
            if (!identifierMatched) {
                return 0;
            }
        }
        int score = 0;
        if (identifierMatched) {
            score += 1000;
        }
        for (String term : terms) {
            String normalized = term.toLowerCase(Locale.ROOT);
            if (haystack.contains(normalized)) {
                score += normalized.length();
            }
        }
        if (unit.unitType() != null && unit.unitType().startsWith("json") && score > 0) {
            score += 10;
        }
        return score;
    }

    private String safeLog(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private String buildRelevantSnippet(KnowledgeDocument doc, String userQuery, int maxLength) {
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        Set<String> terms = extractSearchTerms(userQuery);
        String lower = content.toLowerCase(Locale.ROOT);
        String bestTerm = terms.stream()
                .filter(term -> lower.contains(term.toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(String::length))
                .orElse("");
        if (bestTerm.isBlank()) {
            return truncate(content, maxLength);
        }
        int hit = lower.indexOf(bestTerm.toLowerCase(Locale.ROOT));
        int start = Math.max(0, hit - 300);
        int end = Math.min(content.length(), start + maxLength);
        if (end - start < maxLength && end == content.length()) {
            start = Math.max(0, end - maxLength);
        }
        String prefix = start > 0 ? "..." : "";
        String suffix = end < content.length() ? "..." : "";
        return prefix + content.substring(start, end) + suffix;
    }

    private String buildRelevantSnippet(KnowledgeUnit unit, String userQuery, int maxLength) {
        if (unit == null || unit.content() == null || unit.content().isBlank()) {
            return "";
        }
        if (unit.unitType() != null && unit.unitType().startsWith("json") && unit.content().length() <= maxLength * 3) {
            return unit.content();
        }
        KnowledgeDocument synthetic = KnowledgeDocument.builder()
                .content(unit.content())
                .build();
        return buildRelevantSnippet(synthetic, userQuery, maxLength);
    }

    private record ScoredUnit(KnowledgeUnit unit, int score) {
    }

    private String limitContext(String context) {
        int maxChars = Math.max(500, llmConfig.getRagMaxContextChars());
        if (context == null || context.length() <= maxChars) {
            return context;
        }
        return context.substring(0, Math.max(0, maxChars - 32)) + "\n\n[knowledge context truncated]\n";
    }

    private long elapsedMillis(long startedAtNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    // ========== Audit Log ==========

    public List<KnowledgeAuditLog> getAuditLogs(Long kbId) {
        return auditLogRepository.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    public List<KnowledgeAuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    private void writeAuditLog(Long kbId, Long userId, String username,
                               String operation, String fileName, HttpServletRequest request) {
        try {
            String ip = request != null ? request.getRemoteAddr() : "unknown";
            KnowledgeAuditLog logEntry = KnowledgeAuditLog.builder()
                    .kbId(kbId)
                    .userId(userId)
                    .username(username != null ? username : String.valueOf(userId))
                    .operation(operation)
                    .fileName(fileName)
                    .ipAddress(ip)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(logEntry);
            log.debug("Audit: {} {} in KB {} by user {}", operation, fileName, kbId, userId);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    // ========== Workspace Sync ==========

    public String buildKnowledgeMdContent(Long kbId) {
        var kbOpt = kbRepository.findById(kbId);
        if (kbOpt.isEmpty()) return "";
        KnowledgeBase kb = kbOpt.get();
        List<KnowledgeDocument> docs = docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(kbId);
        if (docs.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(kb.getName()).append("\n\n");
        if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
            sb.append(kb.getDescription()).append("\n\n");
        }
        sb.append("共 ").append(docs.size()).append(" 篇文档\n\n");

        for (KnowledgeDocument doc : docs) {
            sb.append("### ").append(doc.getFileName()).append("\n\n");
            if (doc.getContent() != null && !doc.getContent().isBlank()) {
                sb.append(doc.getContent()).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    @Transactional
    public void syncKnowledgeBaseToAgents(Long kbId) {
        String content = buildKnowledgeMdContent(kbId);
        List<Agent> agents = agentRepository.findByKnowledgeBaseId(kbId);
        for (Agent agent : agents) {
            workspaceInitService.updateKnowledgeMd(agent.getId(), content);
            log.debug("Synced KB {} to agent {}", kbId, agent.getId());
        }
    }

    // ========== Helpers ==========

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }

    private String extractText(MultipartFile file, String ext) throws Exception {
        return switch (ext) {
            case "txt", "md", "csv" -> readTextFile(file);
            case "json", "xml", "yaml", "yml", "properties", "log" -> readTextFile(file);
            default -> "[暂不支持自动提取 " + ext.toUpperCase() + " 格式内容，请在下方手动输入文本内容]\n\n" + readTextFile(file);
        };
    }

    private String readTextFile(MultipartFile file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
