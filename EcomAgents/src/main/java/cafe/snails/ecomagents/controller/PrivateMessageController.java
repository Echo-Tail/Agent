package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.ChatPrivateMessage;
import cafe.snails.ecomagents.repository.ChatPrivateMessageRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

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
