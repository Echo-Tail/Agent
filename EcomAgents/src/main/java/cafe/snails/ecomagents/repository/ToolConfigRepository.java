package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ToolConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工具配置数据访问层，提供对 tool_configs 表的 CRUD 操作。
 */
@Repository
public interface ToolConfigRepository extends JpaRepository<ToolConfig, String> {
    /** 查询所有已启用的工具 */
    List<ToolConfig> findByEnabledTrue();
}
