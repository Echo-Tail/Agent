package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 快照进度状态，对应 Bright Data GET /datasets/v3/progress/{snapshot_id} 响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrightDataSnapshotStatus {
    /** 快照 ID */
    private String snapshotId;

    /** 数据集 ID */
    private String datasetId;

    /** 状态：starting / running / ready / failed */
    private String status;
}
