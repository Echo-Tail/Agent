package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 会话数据访问层。
 */
public interface SessionRepository extends JpaRepository<Session, Long> {
    /** 按文件夹 ID 查找会话 */
    List<Session> findByFolderId(Long folderId);
    /** 按 Agent ID 查找会话 */
    List<Session> findByAgentId(Long agentId);
    /** 查找未归入任何文件夹的会话 */
    List<Session> findByFolderIdIsNull();

    /** 根据 HarnessAgent 会话 ID 查找会话 */
    Optional<Session> findByHarnessSessionId(String harnessSessionId);

    /** 按 ID 查找会话并同时加载 messages 集合（避免 LazyInitializationException） */
    @Query("SELECT s FROM Session s LEFT JOIN FETCH s.messages WHERE s.id = :id")
    Optional<Session> findByIdWithMessages(@Param("id") Long id);
}
