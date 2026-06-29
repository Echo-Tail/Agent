package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GroupMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 群消息仓库，提供历史消息分页、重连补偿和未读状态更新查询。
 */
public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    /** 按时间倒序分页查询群消息（下拉加载历史） */
    List<GroupMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
    /** 查询某个时间点之后的消息（SSE 重连时拉取遗漏消息） */
    List<GroupMessage> findByGroupIdAndCreatedAtAfterOrderByCreatedAtAsc(Long groupId, java.time.LocalDateTime after);

    /** 查询用户在某个群中的未读消息数（排除用户自己发的） */
    @Query("SELECT COUNT(m) FROM GroupMessage m WHERE m.groupId = :groupId AND m.senderId != :userId AND m.read = false")
    long countUnreadByGroupId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 标记群聊中某用户收到的消息为已读 */
    @Modifying
    @Query("UPDATE GroupMessage m SET m.read = true WHERE m.groupId = :groupId AND m.senderId != :userId AND m.read = false")
    int markGroupAsRead(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
