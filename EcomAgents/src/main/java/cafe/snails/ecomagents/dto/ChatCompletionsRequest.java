package cafe.snails.ecomagents.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Chat Completions 兼容的请求体。
 * <p>用于通过 /v1/chat/completions 接口调用图片生成。</p>
 */
@Data
public class ChatCompletionsRequest {
    /** 模型名称，例如 gpt-image-2、dall-e-3 */
    private String model;

    /** 对话消息列表，最后一条 user 消息的 content 作为 prompt */
    private List<ChatMessage> messages;

    /** 生成图片数量 */
    private Integer n;

    /** 图片尺寸，例如 1024x1024 */
    private String size;

    /** 图片质量，例如 standard、hd */
    private String quality;

    /** 是否流式响应（暂不支持） */
    private Boolean stream;

    /** 用户标识 */
    private String user;

    /**
     * 单条对话消息。
     */
    @Data
    public static class ChatMessage {
        private String role;
        private String content;
    }

    /**
     * 提取生成 prompt：取最后一条 role=user 的消息内容。
     */
    public String resolvePrompt() {
        if (messages == null || messages.isEmpty()) return "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("user".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isBlank()) {
                return msg.getContent().trim();
            }
        }
        return "";
    }
}
