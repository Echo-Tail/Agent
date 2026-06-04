package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统日志仓库，支持动态查询、级别/类别统计和错误趋势聚合。
 */
public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {

    /** 按小时统计指定时间之后的错误日志数量。 */
    @Query(value = "SELECT date_trunc('hour', created_at) as hour, COUNT(*) as cnt " +
           "FROM system_logs WHERE level = 'ERROR' AND created_at > :after " +
           "GROUP BY hour ORDER BY hour ASC", nativeQuery = true)
    List<Object[]> countErrorsByHourNative(@Param("after") LocalDateTime after);

    /** 统计指定级别的日志数量。 */
    long countByLevel(String level);

    /** 统计指定类别的日志数量。 */
    long countByCategory(String category);

    /** 统计指定时间之后的日志数量。 */
    long countByCreatedAtAfter(LocalDateTime after);

    /** 统计指定时间之后的 ERROR 日志数量。 */
    @Query("SELECT COUNT(s) FROM SystemLog s WHERE s.level = 'ERROR' AND s.createdAt > :after")
    long countErrorsSince(@Param("after") LocalDateTime after);
}
