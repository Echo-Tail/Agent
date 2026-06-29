package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.GroupFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 群文件仓库。
 */
public interface GroupFileRepository extends JpaRepository<GroupFile, Long> {
    /** 查询群的所有文件 */
    List<GroupFile> findByGroupIdOrderByUploadedAtDesc(Long groupId);
}
