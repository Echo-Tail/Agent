package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.GroupMessage;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.GroupMessageService;
import cafe.snails.ecomagents.service.GroupSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 群消息 + SSE 控制器。
 */
@RestController
@RequestMapping("/v1/groups/{groupId}")
@RequiredArgsConstructor
public class GroupMessageController {

    private final GroupMessageService groupMessageService;
    private final GroupSseService groupSseService;

    /** 发送群消息 */
    @PostMapping("/messages")
    public ApiResponse<GroupMessage> sendMessage(@PathVariable Long groupId,
                                                  @RequestBody Map<String, String> body,
                                                  @CurrentUserId Long userId) {
        String content = body.get("content");
        return groupMessageService.sendMessage(groupId, userId, content);
    }

    /** 获取群消息历史（分页，按时间倒序） */
    @GetMapping("/messages")
    public ApiResponse<List<GroupMessage>> listMessages(@PathVariable Long groupId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return groupMessageService.listMessages(groupId, page, size);
    }

    /** 群 SSE 长连接 */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long groupId) {
        return groupSseService.createEmitter(groupId);
    }
}
