package cafe.snails.ecomagents.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OpenAI Chat Completions 兼容的响应体。
 * <p>图片 URL 嵌入在 choices[0].message.content 中。</p>
 */
@Data
@Builder
@AllArgsConstructor
public class ChatCompletionsResponse {
    /** 请求 ID */
    private String id;

    /** 固定为 "chat.completion" */
    @JsonProperty("object")
    private String object;

    /** 创建时间戳（秒） */
    private long created;

    /** 使用的模型 */
    private String model;

    /** 回复选项列表 */
    private List<Choice> choices;

    /** Token 用量 */
    private Usage usage;

    @Data
    @Builder
    @AllArgsConstructor
    public static class Choice {
        /** 选项索引 */
        private int index;

        /** 回复消息 */
        private Message message;

        /** 结束原因：stop / length / content_filter */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
