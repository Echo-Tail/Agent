package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.BrightDataRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Bright Data 调用记录仓库。
 */
@Repository
public interface BrightDataRecordRepository extends JpaRepository<BrightDataRecord, Long> {

    /** 按用户分页查询，按创建时间倒序 */
    Page<BrightDataRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 按快照 ID 查询 */
    Optional<BrightDataRecord> findBySnapshotId(String snapshotId);

    /** 按状态查询 */
    List<BrightDataRecord> findByStatus(String status);

    /** 按用户 + 类型查询 */
    List<BrightDataRecord> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
}
