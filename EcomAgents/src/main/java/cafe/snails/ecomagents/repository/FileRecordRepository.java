package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {
}
