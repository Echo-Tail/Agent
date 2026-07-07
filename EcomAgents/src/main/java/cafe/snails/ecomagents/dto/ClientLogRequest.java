package cafe.snails.ecomagents.dto;

import lombok.Data;
import java.util.List;

/**
 * 前端客户端日志批量上报请求 DTO。
 * 前端 Logger 缓冲后统一通过 POST /v1/client-logs 上报。
 */
@Data
public class ClientLogRequest {
    /** 日志条目列表。 */
    private List<LogEntry> logs;

    @Data
    public static class LogEntry {
        /** ISO 时间戳。 */
        private String timestamp;
        /** 日志级别：DEBUG / INFO / WARN / ERROR。 */
        private String level;
        /** 来源上下文，如 HTTP、GLOBAL、CONSOLE。 */
        private String context;
        /** 日志消息内容。 */
        private String message;
        /** 可选的结构化数据（JSON 字符串）。 */
        private String data;
    }
}
