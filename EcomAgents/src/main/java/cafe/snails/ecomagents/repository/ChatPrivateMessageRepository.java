package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ChatPrivateMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 私聊消息仓库，提供会话消息查询、未读统计、已读更新和联系人提取。
 */
public interface ChatPrivateMessageRepository extends JpaRepository<ChatPrivateMessage, Long> {
    /** 查询两个用户之间的私聊消息（双向） */
    @Query("SELECT m FROM ChatPrivateMessage m WHERE (m.senderId = :userId1 AND m.receiverId = :userId2) OR (m.senderId = :userId2 AND m.receiverId = :userId1) ORDER BY m.createdAt DESC")
    List<ChatPrivateMessage> findConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2, Pageable pageable);

    /** 查询某用户的未读私聊数，按发送者分组 */
    @Query("SELECT m.senderId, COUNT(m) FROM ChatPrivateMessage m WHERE m.receiverId = :userId AND m.read = false GROUP BY m.senderId")
    List<Object[]> countUnreadByReceiverId(@Param("userId") Long userId);

    /** 标记某用户与另一用户的私聊为已读 */
    @Modifying
    @Query("UPDATE ChatPrivateMessage m SET m.read = true WHERE m.senderId = :otherUserId AND m.receiverId = :userId AND m.read = false")
    int markConversationAsRead(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);

    /** 查询用户联系过的所有用户 ID（排序在 Service 层处理） */
    @Query("SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END FROM ChatPrivateMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<Long> findContactUserIds(@Param("userId") Long userId);
}
