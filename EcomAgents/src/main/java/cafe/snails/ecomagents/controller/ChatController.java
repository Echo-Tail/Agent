package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.service.HarnessChatService;
import cafe.snails.ecomagents.service.SessionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 对话控制器，提供 SSE 流式对话接口。
 * <p>
 * 使用 HarnessAgent 进行 ReAct 推理循环，通过 SSE 推送步骤事件和最终结果。
 * </p>
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final HarnessChatService harnessChatService;
    private final SessionMapper sessionMapper;

    /**
     * SSE 流式对话入口。
     * <p>
     * sessionId 为可选参数：已有会话传入 DB 中的会话 ID，新会话不传或传 null。
     * </p>
     */
    @PostMapping(value = "/{agentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @PathVariable("agentId") Long agentId,
            @RequestBody Map<String, Object> body) {

        Long dbSessionId = body.get("sessionId") != null
                ? Long.valueOf(body.get("sessionId").toString()) : null;
        String content = (String) body.get("content");

        if (content == null || content.isBlank()) {
            log.warn("streamChat rejected: missing content (agentId={})", agentId);
            return errorEmitter("消息内容不能为空");
        }

        log.info("streamChat request: agentId={}, dbSessionId={}, contentLen={}",
                agentId, dbSessionId, content.length());

        // TODO: 替换为从 JWT token 中提取真实 userId
        Long userId = 1L;

        // 通过 SessionMapper 获取或创建 HarnessAgent sessionId（UUID 格式）
        String harnessSessionId = sessionMapper.resolveHarnessSessionId(dbSessionId, agentId, userId);

        // 启动 HarnessAgent 流式对话，SseEmitter 由内部创建并立即返回
        return harnessChatService.streamChat(agentId, harnessSessionId, content, userId);
    }

    /** 返回错误 SseEmitter */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Map<String, Object> event = Map.of("type", "error", "message", message);
            emitter.send(SseEmitter.event().data(" " + new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(event)));
            emitter.complete();
        } catch (Exception ignored) {}
        return emitter;
    }
}
