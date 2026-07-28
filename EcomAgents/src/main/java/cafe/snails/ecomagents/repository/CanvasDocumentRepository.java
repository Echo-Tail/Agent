package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.CanvasDocument;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 画布文档数据访问层。
 */
public interface CanvasDocumentRepository extends JpaRepository<CanvasDocument, Long> {}
