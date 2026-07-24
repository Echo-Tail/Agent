package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AiModelCapability;
import cafe.snails.ecomagents.model.ModelCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AiModelCapabilityRepository extends JpaRepository<AiModelCapability, Long> {
    List<AiModelCapability> findByModelIdOrderById(Long modelId);
    Optional<AiModelCapability> findByModelIdAndCapability(Long modelId, ModelCapability capability);
    void deleteByModelId(Long modelId);
}
