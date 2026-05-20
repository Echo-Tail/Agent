package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.TokenUsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 用量记录数据访问层。
 */
public interface TokenUsageRecordRepository extends JpaRepository<TokenUsageRecord, Long> {

    /** 按日期区间汇总各模型的调用次数和 token 消耗 */
    @Query("""
        SELECT t.modelName, t.modelType,
               COUNT(t), SUM(t.totalTokens), SUM(t.promptTokens), SUM(t.completionTokens)
        FROM TokenUsageRecord t
        WHERE t.createdAt BETWEEN :start AND :end
        GROUP BY t.modelName, t.modelType
        ORDER BY COUNT(t) DESC
        """)
    List<Object[]> sumByModelBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 按日期区间统计图片模型调用次数 */
    @Query("""
        SELECT COUNT(t)
        FROM TokenUsageRecord t
        WHERE t.createdAt BETWEEN :start AND :end
          AND t.modelType = 'IMAGE'
        """)
    Long countImageModelCallsBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 按日期区间查询详细记录，按时间倒序 */
    List<TokenUsageRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end);
}
