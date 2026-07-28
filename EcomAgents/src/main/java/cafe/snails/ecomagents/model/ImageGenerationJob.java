package cafe.snails.ecomagents.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 图片生成任务的配置、进度与执行结果统计。
 */
@Entity
@Table(name = "image_generation_jobs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ImageGenerationJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="user_id", nullable=false) private Long userId;
    @Column(name="model_id", nullable=false) private Long modelId;
    @Column(name="retry_of_job_id") private Long retryOfJobId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ImageGenerationMode mode;
    @Column(nullable=false, columnDefinition="TEXT") private String prompt;
    @Column(name="negative_prompt", columnDefinition="TEXT") private String negativePrompt;
    @Column(name="target_count", nullable=false) private Integer targetCount;
    @Column(name="options_json", columnDefinition="TEXT") private String optionsJson;
    @Column(nullable=false, length=50) private String provider;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ModelProtocol protocol;
    @Column(name="remote_model_name", nullable=false, length=100) private String remoteModelName;
    @Column(name="api_url", nullable=false, length=500) private String apiUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ModelCapability capability;
    @Column(name="credential_id") private Long credentialId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ImageGenerationJobStatus status;
    @Enumerated(EnumType.STRING) @Column(name="execution_phase", length=32) private ImageGenerationExecutionPhase executionPhase;
    @Builder.Default @Column(name="success_count", nullable=false) private Integer successCount = 0;
    @Builder.Default @Column(name="failure_count", nullable=false) private Integer failureCount = 0;
    @Column(name="worker_id", length=100) private String workerId;
    @Column(name="lease_until") private LocalDateTime leaseUntil;
    @Builder.Default @Column(name="attempt_count", nullable=false) private Integer attemptCount = 0;
    @Column(name="next_attempt_at") private LocalDateTime nextAttemptAt;
    @JsonIgnore @Column(name="provider_task_token", columnDefinition="TEXT") private String providerTaskToken;
    @Column(name="provider_status", length=100) private String providerStatus;
    @Column(name="error_code", length=100) private String errorCode;
    @Column(name="safe_error_message", length=500) private String safeErrorMessage;
    @Builder.Default @Column(nullable=false) private Boolean retryable = false;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    @Column(name="started_at") private LocalDateTime startedAt;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
    @Version private Long version;

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = ImageGenerationJobStatus.PENDING;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
