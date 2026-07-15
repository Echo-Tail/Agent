package cafe.snails.ecomagents.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ai_model_capabilities", uniqueConstraints =
        @UniqueConstraint(name = "uk_ai_model_capability", columnNames = {"model_id", "capability"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AiModelCapability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "model_id", nullable = false)
    private Long modelId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModelCapability capability;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModelProtocol protocol;
    @Column(name = "model_name_override", length = 100)
    private String modelNameOverride;
    @Column(name = "api_url_override", length = 500)
    private String apiUrlOverride;
    @Column(name = "credential_id_override")
    private Long credentialIdOverride;
    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;
}
