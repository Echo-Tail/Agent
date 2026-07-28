package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ModelCredential;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 模型访问凭证数据访问层。
 */
public interface ModelCredentialRepository extends JpaRepository<ModelCredential, Long> {
}
