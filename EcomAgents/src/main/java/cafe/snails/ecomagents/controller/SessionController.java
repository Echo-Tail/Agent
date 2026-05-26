package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SessionSummary;
import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器，支持会话 CRUD 和消息追加。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /** 获取会话列表，可按 folderId 或 agentId 筛选，始终按当前用户隔离 */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummary>> listSessions(
            @CurrentUserId Long userId,
            @RequestParam(name = "folderId", required = false) Long folderId,
            @RequestParam(name = "agentId", required = false) Long agentId) {
        return sessionService.listSessions(userId, folderId, agentId);
    }

    /** 获取单个会话详情（含完整消息列表） */
    @GetMapping("/sessions/{id}")
    public ApiResponse<Session> getSession(
            @PathVariable("id") Long id,
            @CurrentUserId Long userId) {
        return sessionService.getSessionWithMessages(id, userId);
    }

    /** 创建新会话 */
    @PostMapping("/sessions")
    public ApiResponse<Session> createSession(
            @RequestBody Map<String, Object> body,
            @CurrentUserId Long userId) {
        Long agentId = body.get("agentId") != null ? Long.valueOf(body.get("agentId").toString()) : null;
        String title = (String) body.get("title");
        Long folderId = body.get("folderId") != null ? Long.valueOf(body.get("folderId").toString()) : null;
        return sessionService.createSession(agentId, title, folderId, userId);
    }

    /** 更新会话标题和/或文件夹 */
    @PutMapping("/sessions/{id}")
    public ApiResponse<Session> updateSession(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @CurrentUserId Long userId) {
        String title = (String) body.get("title");
        Long folderId = body.get("folderId") != null ? Long.valueOf(body.get("folderId").toString()) : null;
        return sessionService.updateSession(id, title, folderId, userId);
    }

    /** 删除会话 */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(
            @PathVariable("id") Long id,
            @CurrentUserId Long userId) {
        return sessionService.deleteSession(id, userId);
    }

    /** 向会话追加一条消息 */
    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<SessionMessage> addMessage(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        return sessionService.addMessage(id, body.get("role"), body.get("content"), userId);
    }
}
