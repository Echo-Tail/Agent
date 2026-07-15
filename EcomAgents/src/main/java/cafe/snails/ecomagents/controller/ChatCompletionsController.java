package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ChatCompletionsRequest;
import cafe.snails.ecomagents.dto.ChatCompletionsResponse;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.image.runtime.ImageGenerationWorkflowService;
import cafe.snails.ecomagents.model.ImageGenerationJobStatus;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.time.Duration;

/**
 * OpenAI Chat Completions 兼容接口。
 * <p>接收标准 OpenAI 格式的请求，将图片生成结果以 Chat Completions 响应格式返回。
 * 主要用于适配外部工具和兼容性需求。</p>
 */
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatCompletionsController {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsController.class);
    private static final String DEFAULT_IMAGE_SIZE = "1254x1254";
    private static final String DEFAULT_IMAGE_QUALITY = "standard";

    private final ImageGenerationWorkflowService imageGenerationWorkflow;

    @Value("${chat.image.wait-timeout-seconds:30}")
    private long imageWaitTimeoutSeconds;

    /**
     * 处理 Chat Completions 请求。
     * <p>从 messages 中提取 user 消息作为 prompt，调用图片生成服务，
     * 返回包含图片 URL 的 OpenAI 兼容响应。</p>
     */
    @PostMapping(value = "/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatCompletionsResponse createChatCompletion(@RequestBody ChatCompletionsRequest request,
                                                          @CurrentUserId Long userId) {
        // 提取 prompt
        String prompt = request.resolvePrompt();
        if (prompt.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "messages 中缺少有效的 user 消息");
        }

        String model = request.getModel() != null ? request.getModel() : "gpt-image-2";
        String size = request.getSize() != null ? request.getSize() : DEFAULT_IMAGE_SIZE;
        String quality = request.getQuality() != null ? request.getQuality() : DEFAULT_IMAGE_QUALITY;
        log.info("ChatCompletions image generation: model={}, prompt=\"{}\", size={}", model, prompt, size);

        var job = imageGenerationWorkflow.submitText(userId, null, prompt, size, quality, 1, null);
        var awaited = imageGenerationWorkflow.await(job.getId(), userId, Duration.ofSeconds(imageWaitTimeoutSeconds));

        String markdownContent;
        if (!awaited.completed()) {
            markdownContent = "图片生成任务已提交，仍在处理中。imageJobId=" + job.getId();
        } else if (awaited.job().getStatus() == ImageGenerationJobStatus.FAILED
                || awaited.job().getStatus() == ImageGenerationJobStatus.CANCELLED
                || awaited.successfulRecords().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    awaited.job().getSafeErrorMessage() != null ? awaited.job().getSafeErrorMessage() : "图片生成失败");
        } else {
            var record = awaited.successfulRecords().get(0);
            markdownContent = String.format("![generated image](%s)", record.getResultPathNormalized());
            if (record.getRevisedPrompt() != null && !record.getRevisedPrompt().isBlank()) {
                markdownContent += "\n\n" + record.getRevisedPrompt();
            }
        }

        long now = System.currentTimeMillis() / 1000;
        String responseId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        ChatCompletionsResponse.Message message = ChatCompletionsResponse.Message.builder()
                .role("assistant")
                .content(markdownContent)
                .build();

        ChatCompletionsResponse.Choice choice = ChatCompletionsResponse.Choice.builder()
                .index(0)
                .message(message)
                .finishReason("stop")
                .build();

        ChatCompletionsResponse.Usage usage = ChatCompletionsResponse.Usage.builder()
                .promptTokens(prompt.length() / 4)
                .completionTokens(100)
                .totalTokens(prompt.length() / 4 + 100)
                .build();

        return ChatCompletionsResponse.builder()
                .id(responseId)
                .object("chat.completion")
                .created(now)
                .model(model)
                .choices(List.of(choice))
                .usage(usage)
                .build();
    }
}
