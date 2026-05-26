package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.UserEmojiFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserEmojiFavoriteRepository extends JpaRepository<UserEmojiFavorite, Long> {
    /** 查询用户收藏的所有表情 */
    List<UserEmojiFavorite> findByUserId(Long userId);
}
