package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bright Data API 调用记录，每次 scrape/trigger 都会写入此表。
 */
@Entity
@Table(name = "bright_data_records", indexes = {
        @Index(name = "idx_bdr_user_id", columnList = "userId"),
        @Index(name = "idx_bdr_snapshot_id", columnList = "snapshotId"),
        @Index(name = "idx_bdr_status", columnList = "status"),
        @Index(name = "idx_bdr_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrightDataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作用户 ID */
    @Column(nullable = false)
    private Long userId;

    /** 调用类型：scrape / trigger */
    @Column(nullable = false, length = 20)
    private String type;

    /** Bright Data 数据集 ID */
    @Column(length = 100)
    private String datasetId;

    /** 异步任务快照 ID（同步直接成功时为 null） */
    @Column(length = 100)
    private String snapshotId;

    /** 状态：success / failed / running / ready */
    @Column(nullable = false, length = 20)
    private String status;

    /** 从输入 URL 中提取的 ASIN 列表，JSON 数组格式 */
    @Column(columnDefinition = "TEXT")
    private String asinList;

    /** 完整请求参数 JSON */
    @Column(columnDefinition = "TEXT")
    private String requestParams;

    /** 返回结果摘要 JSON */
    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    /** 返回记录数 */
    private Integer datasetSize;

    /** 接口调用耗时（毫秒） */
    private Long timeCostMs;

    /** 失败原因 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
