package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SkillIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 技能索引数据访问层。
 */
@Repository
public interface SkillIndexRepository extends JpaRepository<SkillIndex, String> {
}
