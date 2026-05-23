package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.KnowledgeAuditLogRepository;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
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
import java.util.List;

/**
 * 知识库业务逻辑，包括知识库 CRUD、文档管理、全文搜索、RAG 上下文构建和审计日志。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeDocumentRepository docRepository;
    private final KnowledgeAuditLogRepository auditLogRepository;
    private final AgentRepository agentRepository;
    private final WorkspaceInitService workspaceInitService;
    private final VectorEmbeddingService vectorEmbeddingService;

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
            workspaceInitService.updateKnowledgeMd(agent.getId(), null);
        }

        vectorEmbeddingService.deleteByKbId(id);
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

        // Reindex vector embeddings
        vectorEmbeddingService.reindexDocument(kbId, saved.getId(), content);

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

        vectorEmbeddingService.deleteEmbeddings(docId);
        docRepository.deleteById(docId);
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

        List<KnowledgeDocument> docs = docRepository.searchByKeywordAndKbIds(userQuery, kbIds);
        if (docs.isEmpty()) return "";

        StringBuilder context = new StringBuilder("\n\n以下是与用户问题相关的知识库内容:\n\n");
        for (int i = 0; i < Math.min(docs.size(), 5); i++) {
            KnowledgeDocument doc = docs.get(i);
            String snippet = doc.getContent();
            if (snippet.length() > 1000) {
                snippet = snippet.substring(0, 1000) + "...";
            }
            context.append("--- ").append(doc.getFileName()).append(" ---\n")
                    .append(snippet).append("\n\n");
        }
        return context.toString();
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
