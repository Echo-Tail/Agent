package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    /** 查找用户创建或加入的所有群 */
    List<ChatGroup> findByCreatedBy(Long userId);
}
