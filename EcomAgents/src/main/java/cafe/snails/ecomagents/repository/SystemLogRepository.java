package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long>, JpaSpecificationExecutor<SystemLog> {

    @Query(value = "SELECT date_trunc('hour', created_at) as hour, COUNT(*) as cnt " +
           "FROM system_logs WHERE level = 'ERROR' AND created_at > :after " +
           "GROUP BY hour ORDER BY hour ASC", nativeQuery = true)
    List<Object[]> countErrorsByHourNative(@Param("after") LocalDateTime after);

    long countByLevel(String level);

    long countByCategory(String category);

    long countByCreatedAtAfter(LocalDateTime after);

    @Query("SELECT COUNT(s) FROM SystemLog s WHERE s.level = 'ERROR' AND s.createdAt > :after")
    long countErrorsSince(@Param("after") LocalDateTime after);
}
