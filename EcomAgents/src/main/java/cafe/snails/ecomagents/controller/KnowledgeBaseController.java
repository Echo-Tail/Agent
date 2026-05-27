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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库管理控制器，支持知识库 CRUD（管理员）、文档上传/删除、全文检索和审计日志。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final UserRepository userRepository;

    // ===== Knowledge Base CRUD (Admin-only) =====

    @GetMapping("/knowledge-bases")
    public ApiResponse<List<KnowledgeBase>> listKnowledgeBases() {
        return kbService.listKnowledgeBases();
    }

    @GetMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> getKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.getKnowledgeBase(id);
    }

    @PostMapping("/knowledge-bases")
    public ApiResponse<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase kb,
                                                         @CurrentUserId Long userId) {
        return kbService.createKnowledgeBase(kb, userId);
    }

    @PutMapping("/knowledge-bases/{id}")
    public ApiResponse<KnowledgeBase> updateKnowledgeBase(@PathVariable("id") Long id,
                                                          @RequestBody KnowledgeBase kb) {
        return kbService.updateKnowledgeBase(id, kb);
    }

    @DeleteMapping("/knowledge-bases/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable("id") Long id) {
        return kbService.deleteKnowledgeBase(id);
    }

    // ===== Document Management =====

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@PathVariable("kbId") Long kbId) {
        return kbService.listDocuments(kbId);
    }

    @PostMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<KnowledgeDocument> uploadDocument(
            @PathVariable("kbId") Long kbId,
            @RequestParam("file") MultipartFile file,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String username = resolveUsername(userId);
        return kbService.uploadDocument(kbId, file, userId, username, request);
    }

    @PostMapping("/knowledge-bases/{kbId}/documents/batch")
    public ApiResponse<List<KnowledgeDocument>> uploadDocuments(
            @PathVariable("kbId") Long kbId,
            @RequestParam("files") MultipartFile[] files,
            @CurrentUserId Long userId,
            HttpServletRequest request) {
        String username = resolveUsername(userId);
        return kbService.uploadDocuments(kbId, files, userId, username, request);
    }

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

    @GetMapping("/knowledge-bases/search")
    public ApiResponse<List<KnowledgeDocument>> search(@RequestParam("q") String q) {
        return kbService.search(q);
    }

    // ===== Audit Logs =====

    @GetMapping("/knowledge-bases/{kbId}/audit-logs")
    public ApiResponse<List<KnowledgeAuditLog>> getAuditLogs(@PathVariable("kbId") Long kbId) {
        return ApiResponse.success(kbService.getAuditLogs(kbId));
    }

    @GetMapping("/knowledge-bases/audit-logs")
    public ApiResponse<List<KnowledgeAuditLog>> getAllAuditLogs() {
        return ApiResponse.success(kbService.getAllAuditLogs());
    }

    // ===== Helpers =====

    private String resolveUsername(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse(String.valueOf(userId));
    }
}
