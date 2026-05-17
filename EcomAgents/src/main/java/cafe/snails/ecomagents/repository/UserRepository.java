package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /** 按用户名查找用户 */
    Optional<User> findByUsername(String username);
    /** 检查用户名是否已存在 */
    boolean existsByUsername(String username);
}
