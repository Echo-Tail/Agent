package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SessionFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 会话文件夹数据访问层。
 */
public interface SessionFolderRepository extends JpaRepository<SessionFolder, Long> {
    /** 按父文件夹 ID 查找子文件夹 */
    List<SessionFolder> findByParentId(Long parentId);
    /** 查找所有根文件夹（parentId 为 null） */
    List<SessionFolder> findByParentIdIsNull();
    /** 检查指定文件夹下是否有子文件夹 */
    boolean existsByParentId(Long parentId);
}
