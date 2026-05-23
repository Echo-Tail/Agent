package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 20)
    private String operation;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
