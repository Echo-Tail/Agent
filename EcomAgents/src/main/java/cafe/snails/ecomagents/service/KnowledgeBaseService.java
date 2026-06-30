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

    /** 当前服务日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 标识符正则：字母/数字开头，后续可含字母/数字/下划线/连字符，至少 4 位 */
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

    /**
     * 获取所有知识库列表。
     *
     * @return 知识库列表（仅元数据，不包含文档内容）
     */
    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return ApiResponse.success(kbRepository.findAll());
    }

    /**
     * 根据 ID 获取单个知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库信息；不存在时返回 404
     */
    public ApiResponse<KnowledgeBase> getKnowledgeBase(Long id) {
        return kbRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "知识库不存在"));
    }

    /**
     * 创建知识库。
     * <p>自动设置创建日期为当天、创建者为当前用户，忽略请求中传入的 ID。</p>
     *
     * @param kb     知识库基本信息（名称、描述等）
     * @param userId 创建者用户 ID
     * @return 创建后的知识库记录
     */
    public ApiResponse<KnowledgeBase> createKnowledgeBase(KnowledgeBase kb, Long userId) {
        kb.setId(null);
        kb.setCreatedAt(LocalDate.now());
        kb.setCreatedBy(userId);
        return ApiResponse.success("知识库创建成功", kbRepository.save(kb));
    }

    /**
     * 更新知识库资料。
     * <p>部分更新——只修改传入的非 null 字段（name / description），其他字段保持不变。</p>
     *
     * @param id      知识库 ID
     * @param updates 包含待更新字段的对象
     * @return 更新后的知识库；不存在时返回 404
     */
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(Long id, KnowledgeBase updates) {
        return kbRepository.findById(id)
                .map(kb -> {
                    if (updates.getName() != null) kb.setName(updates.getName());
                    if (updates.getDescription() != null) kb.setDescription(updates.getDescription());
                    return ApiResponse.success("知识库更新成功", kbRepository.save(kb));
                })
                .orElseGet(() -> ApiResponse.error(404, "知识库不存在"));
    }

    /**
     * 删除知识库及其所有文档。
     * <p>级联操作：
     * <ul>
     *   <li>解除关联该知识库的所有 Agent 的引用</li>
     *   <li>更新 Agent 工作空间的 KNOWLEDGE.md</li>
     *   <li>清除本地向量索引缓存</li>
     *   <li>删除数据库中的文档记录</li>
     *   <li>删除知识库记录</li>
     * </ul>
     * </p>
     *
     * @param id 知识库 ID
     * @return 操作结果；知识库不存在时返回 404
     */
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

    /**
     * 上传单个文档到知识库。
     * <p>执行流程：检查知识库存在 → 校验文件名 → 提取文本内容 → 保存文档记录 →
     * 触发异步向量索引重建 → 同步知识库到 Agent 工作空间 → 写入审计日志。</p>
     *
     * @param kbId     知识库 ID
     * @param file     上传的文件（支持 txt/md/csv/json/xml/yaml/properties/log 等）
     * @param userId   上传者用户 ID
     * @param username 上传者用户名（用于审计日志）
     * @param request  HTTP 请求（用于提取客户端 IP 写入审计日志）
     * @return 保存后的文档记录
     */
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

    /**
     * 批量上传文档。每个文件独立处理，单个文件失败不影响其他文件。
     * 索引重建和 workspace 同步在所有文件处理完成后一次性触发。
     */
    public ApiResponse<List<KnowledgeDocument>> uploadDocuments(Long kbId, MultipartFile[] files, Long userId,
                                                                String username, HttpServletRequest request) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }

        List<KnowledgeDocument> savedDocs = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                String fileName = file.getOriginalFilename();
                if (fileName == null || fileName.isBlank()) {
                    errors.add("空文件名跳过");
                    continue;
                }

                String ext = getExtension(fileName).toLowerCase();
                String content = extractText(file, ext);

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
                savedDocs.add(saved);

                // Audit log per file
                writeAuditLog(kbId, userId, username, "UPLOAD", fileName, request);
            } catch (Exception e) {
                String name = file.getOriginalFilename();
                log.error("Failed to upload file {}: {}", name, e.getMessage());
                errors.add((name != null ? name : "unknown") + ": " + e.getMessage());
            }
        }

        if (!savedDocs.isEmpty()) {
            localKnowledgeIndexService.rebuildAsync(kbId);
            syncKnowledgeBaseToAgents(kbId);
        }

        String message = "成功上传 " + savedDocs.size() + " 个文件";
        if (!errors.isEmpty()) {
            message += "，" + errors.size() + " 个文件失败";
        }

        return ApiResponse.success(message, savedDocs);
    }

    /**
     * 删除知识库中的单个文档。
     * <p>级联操作：删除数据库记录 → 触发异步向量索引重建 → 同步 Agent 工作空间 → 写入审计日志。</p>
     *
     * @param kbId     知识库 ID
     * @param docId    文档 ID
     * @param userId   操作者用户 ID
     * @param username 操作者用户名（用于审计日志）
     * @param request  HTTP 请求（用于提取客户端 IP）
     * @return 操作结果
     */
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

    /**
     * 全局全文搜索——在所有知识库中搜索包含指定关键词的文档。
     * <p>使用数据库 LIKE 查询，大小写不敏感。关键词为空时返回空列表。</p>
     *
     * @param keyword 搜索关键词
     * @return 匹配的文档列表
     */
    public ApiResponse<List<KnowledgeDocument>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(docRepository.searchByKeyword(keyword.trim()));
    }

    /**
     * 在指定知识库集合中搜索包含关键词的文档。
     * <p>限定搜索范围到传入的 kbIds 列表，用于 Agent 绑定了多个知识库时的定向搜索。</p>
     *
     * @param keyword 搜索关键词
     * @param kbIds   知识库 ID 列表（限定范围）；为空时返回空列表
     * @return 匹配的文档列表
     */
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
        int searchLimit = llmConfig.getRagSearchLimit();

        // Dense vector search
        LocalKnowledgeIndexService.KnowledgeSearchResult vectorResult = localKnowledgeIndexService.searchSimilarDetailed(
                kbIds, userQuery, searchLimit, llmConfig.getRagSimilarityThreshold());

        // Sparse keyword search
        List<ScoredUnit> sparseResults = findRelevantUnitsScored(kbIds, userQuery);

        // RRF fusion
        boolean hasVector = vectorResult != null && !vectorResult.chunks().isEmpty();
        boolean hasSparse = !sparseResults.isEmpty();

        if (!hasVector && !hasSparse) {
            log.info("Knowledge context empty: kbCount={}, elapsedMs={}",
                    kbIds.size(), elapsedMillis(startedAt));
            if (vectorResult != null && vectorResult.timedOut()) {
                return "Knowledge retrieval status: vector_timeout_no_fallback\n\nNo relevant knowledge context is available.";
            }
            return "";
        }

        int K = 60;
        java.util.Map<String, Double> rrfScores = new java.util.HashMap<>();
        java.util.Map<String, String> chunkTexts = new java.util.HashMap<>();

        if (hasVector) {
            for (int i = 0; i < vectorResult.chunks().size(); i++) {
                String key = "dense_" + i;
                rrfScores.put(key, 1.0 / (K + i + 1));
                chunkTexts.put(key, vectorResult.chunks().get(i));
            }
        }

        int sparseRank = 0;
        for (ScoredUnit su : sparseResults) {
            String key = "sparse_" + sparseRank;
            double score = 1.0 / (K + sparseRank + 1);
            rrfScores.merge(key, score, Double::sum);
            KnowledgeUnit unit = su.unit();
            StringBuilder sb = new StringBuilder();
            sb.append("[keyword] ").append(unit.fileName());
            if (unit.sourceLocation() != null && !unit.sourceLocation().isBlank()) {
                sb.append(" @ ").append(unit.sourceLocation());
            }
            sb.append("\n").append(buildRelevantSnippet(unit, userQuery, 1800));
            chunkTexts.put(key, sb.toString());
            sparseRank++;
        }

        List<String> fusedChunks = rrfScores.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(searchLimit)
                .map(e -> chunkTexts.get(e.getKey()))
                .toList();

        // Re-rank fused results by keyword match
        fusedChunks = rerankByKeywords(fusedChunks, userQuery);

        String statusLabel;
        if (hasVector && hasSparse) {
            statusLabel = "hybrid_vector_sparse";
        } else if (hasVector) {
            statusLabel = vectorResult.degraded() ? "degraded_vector_search" : "vector_search";
        } else {
            statusLabel = "text_search_fallback";
        }

        StringBuilder context = new StringBuilder("\n\nKnowledge retrieval status: ")
                .append(statusLabel);
        if (vectorResult != null && vectorResult.timedOut()) {
            context.append(" (partial)");
        }
        context.append("\n\nUse the knowledge context below to answer. Do NOT call web_search or local file tools unless the user explicitly asks for realtime, external, or workspace file information. Do NOT attempt to read knowledge base files directly from the workspace directory - they are only accessible through this knowledge context.\n\n");

        for (int i = 0; i < fusedChunks.size(); i++) {
            context.append("--- chunk ").append(i + 1).append(" ---\n")
                    .append(truncate(fusedChunks.get(i), 3000))
                    .append("\n\n");
        }

        String limited = limitContext(context.toString());
        log.info("Knowledge context built (hybrid): kbCount={}, fusedChunks={}, status={}, elapsedMs={}, returnedChars={}",
                kbIds.size(), fusedChunks.size(), statusLabel, elapsedMillis(startedAt), limited.length());
        return limited;
    }


    /**
     * 返回带有分数的稀疏检索结果，供混合检索（Hybrid RRF）使用。
     */
    private List<ScoredUnit> findRelevantUnitsScored(List<Long> kbIds, String userQuery) {
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
        List<ScoredUnit> scoredUnits = docs.stream()
                .flatMap(doc -> knowledgeUnitParserService.parse(doc).stream())
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
        log.info("Sparse retrieval candidates: docs={}, units={}, selected={}, topScore={}",
                docs.size(), scoredUnits.size(), selected.size(), topScore);
        return selected;
    }

    /**
     * 查找与用户查询相关的知识单元（KnowledgeUnit），用于构建 RAG 上下文。
     * <p>执行两步检索：先用关键词搜索匹配的文档，再将文档解析为知识单元并按相关性打分。
     * 最终选取分数 >= topScore * 75% 的单元，上限由 ragSearchLimit 配置控制。</p>
     *
     * @param kbIds     知识库 ID 列表
     * @param userQuery 用户查询文本
     * @return 按相关性降序排列的知识单元列表
     */
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

    /**
     * 查找与用户查询相关的文档（整文档级别，不拆分为单元）。
     * <p>先用关键词精确匹配搜索，若未命中则对所有文档按关键词评分排序，
     * 取前 5 篇。用于不需要细粒度单元的场景。</p>
     *
     * @param kbIds     知识库 ID 列表
     * @param userQuery 用户查询文本
     * @return 按相关性降序排列的文档列表，最多 5 篇
     */
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

    /**
     * 从查询文本中提取搜索词（term）。
     * <p>对英文按空格分词，过滤 <2 字符和 >80 字符的词；
     * 对中/日/韩文生成 2~4 字的 n-gram 作为额外搜索词。</p>
     *
     * @param query 用户查询文本
     * @return 去重的搜索词集合
     */
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

    /**
     * 从查询中提取标识符形式的搜索词（如变量名、方法名、类名）。
     * <p>匹配正则 [a-zA-Z0-9][a-zA-Z0-9_-]{3,}，用于代码或配置文档的精确匹配。</p>
     *
     * @param query 用户查询文本
     * @return 匹配的标识符集合
     */
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

    /**
     * 为 CJK（中日韩）文本生成 n-gram 搜索词，弥补基于空格的西文分词对东亚文字不适配的问题。
     * <p>按 n=4 → 3 → 2 的顺序，生成长度为 2~4 的连续子串，
     * 总上限 80 个 gram。</p>
     *
     * @param terms 搜索词集合（结果追加到此集合）
     * @param text  输入文本
     */
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

    /**
     * 判断文本是否包含 CJK（中日韩越）统一表意文字。
     * <p>检查字符的 Unicode Script 属性是否为 HAN / HIRAGANA / KATAKANA / HANGUL。</p>
     *
     * @param text 输入文本
     * @return 包含 CJK 字符时返回 true
     */
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

    /**
     * 计算文档与搜索词的匹配得分。
     * <p>遍历搜索词，统计文档内容中包含每个词的次数 × 词长，累加作为总分。</p>
     *
     * @param doc   知识文档
     * @param terms 搜索词集合
     * @return 匹配得分（0 表示无匹配）
     */
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

    /**
     * 计算知识单元与搜索词的匹配得分。
     * <p>优先匹配标识符（identifier），命中标识符 +1000 分保底；
     * 然后叠加普通搜索词匹配分；JSON 类型的单元额外 +10 分。</p>
     *
     * @param unit        知识单元
     * @param terms       搜索词集合
     * @param identifiers 标识符集合（用于代码/配置精确匹配）
     * @return 匹配得分；0 表示无匹配
     */
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

    /**
     * 截断日志输出中的长字符串，防止日志被内容撑爆。
     *
     * @param value 原始字符串
     * @return 不超过 80 字符的截断结果
     */
    private String safeLog(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }

    /**
     * 截断字符串到指定长度，超长部分替换为 "..."。
     *
     * @param text      原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    /**
     * 从文档中提取与用户查询最相关的片段（snippet）。
     * <p>找到查询词在文档中的首次命中位置，截取前后各 300 字符，
     * 总长不超过 maxLength。用于构建 RAG 上下文中的引用块。</p>
     *
     * @param doc       知识文档
     * @param userQuery 用户查询
     * @param maxLength 片段最大字符数
     * @return 相关片段文本（两端有 ... 表示被截断）
     */
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

    /**
     * 从知识单元中提取与查询最相关的片段。
     * <p>JSON 类型的单元直接返回完整内容（≤ 3×maxLength），
     * 其余类型委托给基于文档的 {@link #buildRelevantSnippet(KnowledgeDocument, String, int)}。</p>
     *
     * @param unit      知识单元
     * @param userQuery 用户查询
     * @param maxLength 片段最大字符数
     * @return 相关片段文本
     */
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

    /**
     * 知识单元和关键词分数的临时结构，用于本地重排序。
     */
    private record ScoredUnit(KnowledgeUnit unit, int score) {
    }

    /**
     * 关键词重排序：对 RRF 融合后的结果进行第二遍精排。
     * 基于查询词覆盖度 + 精确短语匹配加分。
     */
    private List<String> rerankByKeywords(List<String> chunks, String userQuery) {
        if (chunks == null || chunks.size() <= 1 || userQuery == null || userQuery.isBlank()) {
            return chunks;
        }

        // Extract unique query terms
        String[] queryTerms = userQuery.toLowerCase().split("[\\s,;.:]+");
        java.util.Set<String> termSet = new java.util.HashSet<>();
        for (String t : queryTerms) {
            String trimmed = t.trim();
            if (trimmed.length() >= 2) termSet.add(trimmed);
        }
        if (termSet.isEmpty()) return chunks;

        // Also extract bigrams for CJK text
        java.util.Set<String> cjkGrams = new java.util.HashSet<>();
        for (String t : termSet) {
            if (t.length() <= 3 && t.chars().anyMatch(c -> Character.isIdeographic(c))) {
                for (int i = 0; i < t.length() - 1; i++) {
                    cjkGrams.add(t.substring(i, i + 2));
                }
            }
        }
        termSet.addAll(cjkGrams);

        // Score each chunk: keyword coverage + density
        List<java.util.AbstractMap.SimpleEntry<String, Double>> scored = new ArrayList<>();
        String exactPhrase = userQuery.toLowerCase().trim();

        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) continue;
            String lower = chunk.toLowerCase();
            int matchCount = 0;
            for (String term : termSet) {
                int idx = lower.indexOf(term);
                while (idx >= 0) {
                    matchCount++;
                    idx = lower.indexOf(term, idx + 1);
                }
            }

            // Coverage: how many unique terms matched
            long uniqueMatches = termSet.stream().filter(t -> lower.contains(t)).count();
            double coverage = (double) uniqueMatches / Math.max(1, termSet.size());

            // Density: match count / chunk length
            double density = (double) matchCount / Math.max(1, chunk.length());

            // Exact phrase boost
            double phraseBoost = lower.contains(exactPhrase) ? 0.3 : 0.0;

            // Combined score: coverage weighted higher, density and phrase as bonuses
            double score = coverage * 2.0 + density * 5.0 + phraseBoost;
            scored.add(new java.util.AbstractMap.SimpleEntry<>(chunk, score));
        }

        return scored.stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .map(java.util.Map.Entry::getKey)
                .toList();
    }

    /**
     * 按配置限制注入模型的知识上下文长度，避免提示词过长。
     */
    private String limitContext(String context) {
        int maxChars = Math.max(500, llmConfig.getRagMaxContextChars());
        if (context == null || context.length() <= maxChars) {
            return context;
        }
        return context.substring(0, Math.max(0, maxChars - 32)) + "\n\n[knowledge context truncated]\n";
    }

    /**
     * 计算从指定纳秒时间点到当前的耗时毫秒数。
     */
    private long elapsedMillis(long startedAtNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    // ========== Audit Log ==========

    public List<KnowledgeAuditLog> getAuditLogs(Long kbId) {
        return auditLogRepository.findByKbIdOrderByCreatedAtDesc(kbId);
    }

    /**
     * 获取所有知识库审计日志，按创建时间倒序返回。
     */
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

    /**
     * 将指定知识库当前内容同步到所有关联 Agent 的 KNOWLEDGE.md。
     */
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

    /**
     * 获取文件名的扩展名（不含点号）。
     *
     * @param fileName 文件名
     * @return 扩展名（小写），无扩展名时返回空字符串
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }

    /**
     * 根据文件扩展名提取文本内容。
     * <p>支持的格式：txt / md / csv / json / xml / yaml / properties / log 直接读取 UTF-8 文本；
     * 其他格式（如 PDF/DOCX）则读取原始文本并附加暂不支持提示。</p>
     *
     * @param file 上传的文件
     * @param ext  文件扩展名
     * @return 提取的文本内容
     * @throws Exception 文件读取失败时抛出
     */
    private String extractText(MultipartFile file, String ext) throws Exception {
        return switch (ext) {
            case "txt", "md", "csv" -> readTextFile(file);
            case "json", "xml", "yaml", "yml", "properties", "log" -> readTextFile(file);
            default -> "[暂不支持自动提取 " + ext.toUpperCase() + " 格式内容，请在下方手动输入文本内容]\n\n" + readTextFile(file);
        };
    }

    /**
     * 以 UTF-8 编码读取上传文件的全部文本内容。
     *
     * @param file 上传的 Multipart 文件
     * @return 文件文本内容（已 trim）
     * @throws Exception 读取失败时抛出
     */
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
