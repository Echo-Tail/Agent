package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GroupMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {
    /** 按时间倒序分页查询群消息（下拉加载历史） */
    List<GroupMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
    /** 查询某个时间点之后的消息（SSE 重连时拉取遗漏消息） */
    List<GroupMessage> findByGroupIdAndCreatedAtAfterOrderByCreatedAtAsc(Long groupId, java.time.LocalDateTime after);

    /** 统计某个时间点之后的未读消息数 */
    long countByGroupIdAndCreatedAtAfter(Long groupId, java.time.LocalDateTime after);
}
