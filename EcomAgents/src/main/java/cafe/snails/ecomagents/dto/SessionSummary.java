package cafe.snails.ecomagents.dto;

import lombok.Data;
import java.util.List;

/**
 * 会话摘要 DTO，用于会话列表展示，包含最后一条消息预览和消息数量。
 */
@Data
public class SessionSummary {
    /** 会话 ID */
    private Long id;
    /** 关联的 Agent ID */
    private Long agentId;
    /** 会话标题 */
    private String title;
    /** 所属文件夹 ID */
    private Long folderId;
    /** 标签 */
    private List<String> tags;
    /** 消息总数 */
    private int messageCount;
    /** 最后一条消息预览 */
    private SessionMessageDTO lastMessage;
    /** 创建时间 */
    private java.time.LocalDateTime createdAt;
    /** 最后更新时间 */
    private java.time.LocalDateTime updatedAt;

    /** 会话消息预览 DTO，仅包含摘要信息 */
    @Data
    public static class SessionMessageDTO {
        /** 角色：user / assistant */
        private String role;
        /** 消息内容（可能截断） */
        private String content;
        /** 时间戳 */
        private java.time.LocalDateTime timestamp;
    }
}
