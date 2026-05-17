package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器，支持知识库 CRUD、文档上传和全文检索。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    // ===== Knowledge Base CRUD =====

    /** 获取所有知识库 */
    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return kbService.listKnowledgeBases();
    }

    /** 获取知识库详情 */
    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> getKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.getKnowledgeBase(id);
    }

    /** 创建知识库 */
    @PostMapping("/knowledge-bases")
    public ApiResponse<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase kb,
                                                         @CurrentUserId Long userId) {
        return kbService.createKnowledgeBase(kb, userId);
    }

    /** 更新知识库 */
    @PutMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(@PathVariable("id") Long id, @RequestBody KnowledgeBase kb) {
        return kbService.updateKnowledgeBase(id, kb);
    }

    /** 删除知识库 */
    @DeleteMapping("/knowledge-bases/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.deleteKnowledgeBase(id);
    }

    // ===== Document Management =====

    /** 获取知识库下的文档列表 */
    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@PathVariable("kbId") Long kbId) {
        return kbService.listDocuments(kbId);
    }

    /** 上传文档到知识库 */
    @PostMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<KnowledgeDocument> uploadDocument(
            @PathVariable("kbId") Long kbId,
            @RequestParam("file") MultipartFile file,
            @CurrentUserId Long userId) {
        return kbService.uploadDocument(kbId, file, userId);
    }

    /** 删除知识库下的某个文档 */
    @DeleteMapping("/knowledge-bases/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(@PathVariable("kbId") Long kbId, @PathVariable("docId") Long docId) {
        return kbService.deleteDocument(kbId, docId);
    }

    // ===== Search =====

    /** 全文搜索知识库文档内容 */
    @GetMapping("/knowledge-bases/search")
    public ApiResponse<List<KnowledgeDocument>> search(@RequestParam("q") String q) {
        return kbService.search(q);
    }
}
