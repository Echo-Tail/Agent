package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.AssetSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 素材空间数据访问层。
 */
public interface AssetSpaceRepository extends JpaRepository<AssetSpace, Long> {
    /** 按名称查询素材空间。 */
    Optional<AssetSpace> findByName(String name);
    /** 判断指定名称的素材空间是否存在。 */
    boolean existsByName(String name);
}
