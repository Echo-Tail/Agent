package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.TokenUsageRecord;
import cafe.snails.ecomagents.repository.TokenUsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 用量统计服务，负责记录和查询 LLM 调用用量。
 */
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    private final TokenUsageRecordRepository repository;

    /** 记录一次 LLM 调用 */
    public void record(TokenUsageRecord record) {
        record.setCreatedAt(LocalDateTime.now());
        repository.save(record);
    }

    /** 按日期区间查询各模型的调用统计 */
    public List<Map<String, Object>> getSummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> rows = repository.sumByModelBetween(start, end);

        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modelName", row[0]);
            m.put("modelType", row[1]);
            m.put("callCount", row[2]);
            m.put("totalTokens", row[3]);
            m.put("promptTokens", row[4]);
            m.put("completionTokens", row[5]);
            return m;
        }).toList();
    }

    /** 按日期区间统计图片模型调用次数 */
    public Long getImageModelCallCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return repository.countImageModelCallsBetween(start, end);
    }

    /** 按日期区间查询详细记录 */
    public List<TokenUsageRecord> getDetailByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return repository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
    }
}
