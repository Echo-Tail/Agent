package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 同步抓取响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrightDataScrapeResponse {
    /** 抓取到的数据记录列表 */
    private List<Map<String, Object>> records;

    /** 接口调用耗时（毫秒） */
    private long timeCostMs;

    /** 超时降级时返回的快照 ID */
    private String snapshotId;

    /** 提示消息 */
    private String message;

    /** 本地数据库记录 ID */
    private long recordId;
}
