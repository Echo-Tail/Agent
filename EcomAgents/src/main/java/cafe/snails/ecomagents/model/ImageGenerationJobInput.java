package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="image_generation_job_inputs", uniqueConstraints=@UniqueConstraint(
        name="uk_image_job_input", columnNames={"job_id","input_index"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ImageGenerationJobInput {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="job_id", nullable=false) private Long jobId;
    @Column(name="input_index", nullable=false) private Integer inputIndex;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=32) private ImageJobInputRole role;
    @Enumerated(EnumType.STRING) @Column(name="source_type", nullable=false, length=32) private ImageJobInputSourceType sourceType;
    @Column(name="source_id") private Long sourceId;
    @Column(name="snapshot_path", nullable=false, length=500) private String snapshotPath;
    @Column(name="mime_type", nullable=false, length=100) private String mimeType;
    @Column(name="file_size", nullable=false) private Long fileSize;
    @Column(nullable=false, length=64) private String sha256;
}
