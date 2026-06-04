package cafe.snails.ecomagents.dto;

import lombok.Data;

@Data
/**
 * 系统日志上报请求 DTO，通常由前端或服务端内部接口提交。
 */
public class SystemLogRequest {
    /** 日志级别，例如 info、warn、error。 */
    private String level;
    /** 日志类别，用于区分业务域或来源。 */
    private String category;
    /** 日志摘要消息。 */
    private String message;
    /** 结构化上下文数据，通常为 JSON 字符串。 */
    private String data;
    /** 操作耗时，单位通常为毫秒。 */
    private Long duration;
    /** 触发日志的路由或接口路径。 */
    private String route;
    /** 关联用户 ID，匿名上报时可为空。 */
    private Long userId;
}
