package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SessionFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 会话文件夹数据访问层。
 */
public interface SessionFolderRepository extends JpaRepository<SessionFolder, Long> {
}
