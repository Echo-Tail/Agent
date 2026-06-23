package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步触发响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrightDataTriggerResponse {
    /** Bright Data 快照 ID，用于后续轮询和下载 */
    private String snapshotId;

    /** 数据集 ID */
    private String datasetId;

    /** 本地数据库记录 ID */
    private long recordId;
}
