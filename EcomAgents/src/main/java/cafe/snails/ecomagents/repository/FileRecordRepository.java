package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 文件记录（FileRecord）数据访问层。
 * <p>提供按上传者 + 对话上下文查询文件列表的能力。</p>
 */
public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    /**
     * 按上传者 ID、对话上下文类型和 ID 查询文件列表，按上传时间降序排列。
     *
     * @param uploadedBy 上传者用户 ID
     * @param contextType 对话上下文类型（PRIVATE / AGENT）
     * @param contextId 对话上下文 ID（对方用户 ID 或 Agent ID）
     * @return 匹配的文件记录列表，按上传时间从新到旧
     */
    List<FileRecord> findByUploadedByAndContextTypeAndContextIdOrderByUploadedAtDesc(
            Long uploadedBy, String contextType, Long contextId);
}
