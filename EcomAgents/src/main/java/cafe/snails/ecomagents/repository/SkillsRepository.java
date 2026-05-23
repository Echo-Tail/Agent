package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Skills;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillsRepository extends JpaRepository<Skills, Long> {
    Optional<Skills> findByName(String name);
    void deleteByName(String name);
}
