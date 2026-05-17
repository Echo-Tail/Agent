package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.KnowledgeBaseRepository;
import cafe.snails.ecomagents.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库业务逻辑，包括知识库 CRUD、文档管理、全文搜索和 RAG 上下文构建。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeDocumentRepository docRepository;

    // ========== Knowledge Base CRUD ==========

    /** 获取所有知识库 */
    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return ApiResponse.success(kbRepository.findAll());
    }

    /** 根据 ID 获取知识库详情 */
    public ApiResponse<KnowledgeBase> getKnowledgeBase(Long id) {
        return kbRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "知识库不存在"));
    }

    /** 创建知识库，自动填充创建时间和创建者 */
    public ApiResponse<KnowledgeBase> createKnowledgeBase(KnowledgeBase kb, Long userId) {
        kb.setId(null);
        kb.setCreatedAt(LocalDate.now());
        kb.setCreatedBy(userId);
        return ApiResponse.success("知识库创建成功", kbRepository.save(kb));
    }

    /** 更新知识库名称和描述 */
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(Long id, KnowledgeBase updates) {
        return kbRepository.findById(id)
                .map(kb -> {
                    if (updates.getName() != null) kb.setName(updates.getName());
                    if (updates.getDescription() != null) kb.setDescription(updates.getDescription());
                    return ApiResponse.success("知识库更新成功", kbRepository.save(kb));
                })
                .orElseGet(() -> ApiResponse.error(404, "知识库不存在"));
    }

    /** 删除知识库及其下所有文档 */
    public ApiResponse<Void> deleteKnowledgeBase(Long id) {
        if (!kbRepository.existsById(id)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        List<KnowledgeDocument> docs = docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(id);
        docRepository.deleteAll(docs);
        kbRepository.deleteById(id);
        return ApiResponse.success("知识库已删除", null);
    }

    // ========== Document Management ==========

    /** 获取指定知识库下的所有文档 */
    public ApiResponse<List<KnowledgeDocument>> listDocuments(Long kbId) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        return ApiResponse.success(docRepository.findByKnowledgeBaseIdOrderByUploadedAtDesc(kbId));
    }

    /** 上传文档到知识库，自动提取文本内容 */
    public ApiResponse<KnowledgeDocument> uploadDocument(Long kbId, MultipartFile file, Long userId) {
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

        return ApiResponse.success("文档上传成功", docRepository.save(doc));
    }

    /** 删除指定知识库下的某个文档 */
    public ApiResponse<Void> deleteDocument(Long kbId, Long docId) {
        if (!kbRepository.existsById(kbId)) {
            return ApiResponse.error(404, "知识库不存在");
        }
        if (!docRepository.existsById(docId)) {
            return ApiResponse.error(404, "文档不存在");
        }
        docRepository.deleteById(docId);
        return ApiResponse.success("文档已删除", null);
    }

    // ========== Search ==========

    /** 全量搜索知识库文档内容 */
    public ApiResponse<List<KnowledgeDocument>> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(docRepository.searchByKeyword(keyword.trim()));
    }

    /** 在指定知识库范围内搜索文档内容 */
    public ApiResponse<List<KnowledgeDocument>> searchInKbs(String keyword, List<Long> kbIds) {
        if (keyword == null || keyword.isBlank() || kbIds == null || kbIds.isEmpty()) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(docRepository.searchByKeywordAndKbIds(keyword.trim(), kbIds));
    }

    // ========== Chat Integration ==========

    /**
     * 构建 RAG 知识库上下文文本，用于注入 LLM 提示词。
     * 从关联的知识库中检索与用户查询相关的文档片段，最多返回 5 段。
     */
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

    // ========== Helpers ==========

    /** 获取文件扩展名 */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }

    /** 根据扩展名提取文本内容 */
    private String extractText(MultipartFile file, String ext) throws Exception {
        return switch (ext) {
            case "txt", "md", "csv" -> readTextFile(file);
            case "json", "xml", "yaml", "yml", "properties", "log" -> readTextFile(file);
            default -> "[暂不支持自动提取 " + ext.toUpperCase() + " 格式内容，请在下方手动输入文本内容]\n\n"
                    + readTextFile(file);
        };
    }

    /** 以 UTF-8 读取上传文件的文本内容 */
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
