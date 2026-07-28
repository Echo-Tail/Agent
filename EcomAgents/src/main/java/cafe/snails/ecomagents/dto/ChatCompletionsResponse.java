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

    /**
     * 单个对话补全候选项。
     */
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

    /**
     * 对话补全返回的消息内容。
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class Message {
        /** 消息发送方角色。 */
        private String role;
        /** 消息正文。 */
        private String content;
    }

    /**
     * 本次对话补全请求的 Token 用量。
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class Usage {
        /** 输入提示词消耗的 Token 数。 */
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        /** 模型生成内容消耗的 Token 数。 */
        @JsonProperty("completion_tokens")
        private int completionTokens;

        /** 本次请求消耗的 Token 总数。 */
        @JsonProperty("total_tokens")
        private int totalTokens;
    }
}
