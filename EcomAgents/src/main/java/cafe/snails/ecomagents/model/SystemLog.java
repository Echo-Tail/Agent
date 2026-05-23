package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_logs", indexes = {
        @Index(name = "idx_system_logs_created_at", columnList = "createdAt"),
        @Index(name = "idx_system_logs_level", columnList = "level"),
        @Index(name = "idx_system_logs_category", columnList = "category")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String level;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String data;

    private Long duration;

    @Column(length = 200)
    private String route;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
