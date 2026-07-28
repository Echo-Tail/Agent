package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理控制器，支持知识库 CRUD（管理员）、文档上传/删除、全文检索和审计日志。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    /** 知识库业务服务。 */
    private final KnowledgeBaseService kbService;
    /** 用户仓库，用于将当前用户 ID 转换为审计日志用户名。 */
    private final UserRepository userRepository;

    // ===== Knowledge Base CRUD (Admin-only) =====

    /** 查询全部知识库。 */
    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return kbService.listKnowledgeBases();
    }

    /** 查询单个知识库详情。 */
    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> getKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.getKnowledgeBase(id);
    }

    /** 创建知识库。 */
    @PostMapping("/knowledge-bases")
    public ApiResponse<KnowledgeBase> createKnowledgeBase(@Valid @RequestBody KnowledgeBase kb,
                                                         @CurrentUserId Long userId) {
        return kbService.createKnowledgeBase(kb, userId);
    }

    /** 更新知识库基本信息。 */
    @PutMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(@PathVariable("id") Long id,
                                                          @Valid @RequestBody KnowledgeBase kb) {
        return kbService.updateKnowledgeBase(id, kb);
    }

    /** 删除知识库及其文档和关联 Agent 引用。 */
    @DeleteMapping("/knowledge-bases/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.deleteKnowledgeBase(id);
    }

    // ===== Document Management =====

    /** 查询指定知识库下的文档列表。 */
    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@PathVariable("kbId") Long kbId) {
        return kbService.listDocuments(kbId);
    }

    /** 向知识库上传单个文档。 */
    @PostMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<KnowledgeDocument> uploadDocument(
            @PathVariable("kbId") Long kbId,
            @RequestParam("file") MultipartFile file,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String username = resolveUsername(userId);
        return kbService.uploadDocument(kbId, file, userId, username, request);
    }

    /** 向知识库批量上传文档。 */
    @PostMapping("/knowledge-bases/{kbId}/documents/batch")
    public ApiResponse<List<KnowledgeDocument>> uploadDocuments(
            @PathVariable("kbId") Long kbId,
            @RequestParam("files") MultipartFile[] files,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String username = resolveUsername(userId);
        return kbService.uploadDocuments(kbId, files, userId, username, request);
    }

    /** 删除知识库中的指定文档。 */
    @DeleteMapping("/knowledge-bases/{kbId}/documents/{docId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable("kbId") Long kbId,
            @PathVariable("docId") Long docId,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String username = resolveUsername(userId);
        return kbService.deleteDocument(kbId, docId, userId, username, request);
    }

    // ===== Search =====

    /** 跨知识库搜索文档内容。 */
    @GetMapping("/knowledge-bases/search")
    public ApiResponse<List<KnowledgeDocument>> search(@RequestParam("q") String q) {
        return kbService.search(q);
    }

    // ===== Audit Logs =====

    /** 查询指定知识库的审计日志。 */
    @GetMapping("/knowledge-bases/{kbId}/audit-logs")
    public ApiResponse<List<KnowledgeAuditLog>> getAuditLogs(@PathVariable("kbId") Long kbId) {
        return ApiResponse.success(kbService.getAuditLogs(kbId));
    }

    /** 查询全部知识库审计日志。 */
    @GetMapping("/knowledge-bases/audit-logs")
    public ApiResponse<List<KnowledgeAuditLog>> getAllAuditLogs() {
        return ApiResponse.success(kbService.getAllAuditLogs());
    }

    // ===== Helpers =====

    /**
     * 根据用户 ID 解析用户名，用户不存在时退回为 ID 字符串。
     */
    private String resolveUsername(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse(String.valueOf(userId));
    }
}
