package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Skills;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
/**
 * 全局技能仓库。
 */
public interface SkillsRepository extends JpaRepository<Skills, Long> {
    /** 按技能名称查询技能元数据。 */
    Optional<Skills> findByName(String name);
    /** 按技能名称删除技能元数据。 */
    void deleteByName(String name);
}
