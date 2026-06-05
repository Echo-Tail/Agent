package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.ChatPrivateMessage;
import cafe.snails.ecomagents.repository.ChatPrivateMessageRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户私聊控制器。
 */
@RestController
@RequestMapping("/v1/messages")
@RequiredArgsConstructor
public class PrivateMessageController {

    private final ChatPrivateMessageRepository privateMessageRepository;
    private final UserRepository userRepository;
    private final SseService sseService;

    /** 发送私聊消息 */
    @PostMapping
    public ApiResponse<ChatPrivateMessage> sendMessage(@RequestBody Map<String, Object> body,
                                                        @CurrentUserId Long userId) {
        Long receiverId = body.get("receiverId") != null ? Long.valueOf(body.get("receiverId").toString()) : null;
        String content = (String) body.get("content");
        if (receiverId == null) return ApiResponse.error(400, "缺少 receiverId");
        if (content == null || content.isBlank()) return ApiResponse.error(400, "消息内容不能为空");

        ChatPrivateMessage msg = ChatPrivateMessage.builder()
                .senderId(userId)
                .receiverId(receiverId)
                .content(content.trim())
                .build();
        msg = privateMessageRepository.save(msg);

        // SSE 推送新消息给接收方
        sseService.broadcast(receiverId, "message", Map.of(
                "id", msg.getId(),
                "senderId", msg.getSenderId(),
                "receiverId", msg.getReceiverId(),
                "content", msg.getContent(),
                "createdAt", msg.getCreatedAt().toString()
        ));
        // SSE 推送未读事件给接收方
        long unreadCount = privateMessageRepository.countUnreadByReceiverId(receiverId).stream()
                .filter(r -> ((Long)r[0]).equals(userId))
                .mapToLong(r -> (Long) r[1])
                .sum() + 1; // +1 因为刚发送的消息还未计入
        sseService.broadcast(receiverId, "unread_private", Map.of(
                "userId", userId,
                "count", unreadCount
        ));

        return ApiResponse.success("消息已发送", msg);
    }

    /** 获取与某用户的私聊历史 */
    @GetMapping("/{otherUserId}")
    public ApiResponse<List<ChatPrivateMessage>> getConversation(@PathVariable Long otherUserId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "50") int size,
                                                                  @CurrentUserId Long userId) {
        var messages = privateMessageRepository.findConversation(userId, otherUserId, PageRequest.of(page, size));
        return ApiResponse.success(messages);
    }

    /** 获取私聊未读汇总 */
    @GetMapping("/unread-summary")
    public ApiResponse<List<Map<String, Object>>> getUnreadSummary(@CurrentUserId Long userId) {
        List<Object[]> rows = privateMessageRepository.countUnreadByReceiverId(userId);
        List<Map<String, Object>> result = rows.stream().map(row -> {
            Long senderId = (Long) row[0];
            Long count = (Long) row[1];
            return Map.<String, Object>of("userId", senderId, "count", count);
        }).toList();
        return ApiResponse.success(result);
    }

    /** 标记私聊会话为已读 */
    @PutMapping("/{otherUserId}/read")
    @Transactional
    public ApiResponse<Void> markAsRead(@PathVariable Long otherUserId, @CurrentUserId Long userId) {
        privateMessageRepository.markConversationAsRead(userId, otherUserId);
        return ApiResponse.success("已标记为已读", null);
    }

    /** 私聊 SSE 长连接 */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CurrentUserId Long userId) {
        return sseService.createEmitter(userId);
    }

    /** 获取最近联系人列表 */
    @GetMapping("/contacts")
    public ApiResponse<List<Map<String, Object>>> getContacts(@CurrentUserId Long userId) {
        List<Long> contactIds = privateMessageRepository.findContactUserIds(userId);

        // 获取联系人的用户名
        var contacts = contactIds.stream()
                .map(id -> userRepository.findById(id)
                        .map(user -> Map.<String, Object>of("userId", id, "username", user.getUsername()))
                        .orElse(null))
                .filter(c -> c != null)
                .collect(Collectors.toList());

        return ApiResponse.success(contacts);
    }
}
