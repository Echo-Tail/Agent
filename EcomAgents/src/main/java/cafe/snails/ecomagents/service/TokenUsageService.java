package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.ModelPriceConfig;
import cafe.snails.ecomagents.model.TokenUsageRecord;
import cafe.snails.ecomagents.repository.TokenUsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    @Value("${token-pricing.usd-cny-rate:6.8}")
    private BigDecimal usdCnyRate;

    /** 记录一次 LLM 调用 */
    public void record(TokenUsageRecord record) {
        record.setCreatedAt(LocalDateTime.now());
        repository.save(record);
    }

    /** 按日期区间查询各 Agent + 模型的调用统计，含费用计算 */
    public List<Map<String, Object>> getSummaryByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Object[]> rows = repository.sumByModelBetween(start, end);

        return rows.stream().map(row -> {
            String agentName = (String) row[0];
            String modelName = (String) row[1];
            String modelType = (String) row[2];
            String username = (String) row[3];
            long callCount = row[4] != null ? ((Number) row[4]).longValue() : 0;
            long totalTokens = row[5] != null ? ((Number) row[5]).longValue() : 0;
            int promptTokens = row[6] != null ? ((Number) row[6]).intValue() : 0;
            int completionTokens = row[7] != null ? ((Number) row[7]).intValue() : 0;

            // 计算 CNY 费用
            ModelPriceConfig pricing = ModelPriceConfig.match(modelName);
            BigDecimal cnyCost = pricing != null
                    ? pricing.calculateCost(promptTokens, completionTokens, usdCnyRate)
                    : BigDecimal.ZERO.setScale(2);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("agentName", agentName);
            m.put("modelName", modelName);
            m.put("modelType", modelType);
            m.put("username", username);
            m.put("callCount", callCount);
            m.put("totalTokens", totalTokens);
            m.put("promptTokens", promptTokens);
            m.put("completionTokens", completionTokens);
            m.put("cnyCost", cnyCost);
            return m;
        }).toList();
    }

    /** 按日期区间统计图片模型调用次数 */
    public Long getImageModelCallCount(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return repository.countImageModelCallsBetween(start, end);
    }

    /** 按日期区间查询详细记录，含费用计算 */
    public List<Map<String, Object>> getDetailByDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        List<TokenUsageRecord> records = repository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);

        return records.stream().map(r -> {
            int promptTokens = r.getPromptTokens() != null ? r.getPromptTokens() : 0;
            int completionTokens = r.getCompletionTokens() != null ? r.getCompletionTokens() : 0;

            ModelPriceConfig pricing = ModelPriceConfig.match(r.getModelName());
            BigDecimal cnyCost = pricing != null
                    ? pricing.calculateCost(promptTokens, completionTokens, usdCnyRate)
                    : BigDecimal.ZERO.setScale(2);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("modelId", r.getModelId());
            m.put("modelName", r.getModelName());
            m.put("modelType", r.getModelType());
            m.put("userId", r.getUserId());
            m.put("agentId", r.getAgentId());
            m.put("agentName", r.getAgentName());
            m.put("username", r.getUsername());
            m.put("promptTokens", promptTokens);
            m.put("completionTokens", completionTokens);
            m.put("totalTokens", r.getTotalTokens());
            m.put("success", r.getSuccess());
            m.put("errorMessage", r.getErrorMessage());
            m.put("createdAt", r.getCreatedAt());
            m.put("cnyCost", cnyCost);
            return m;
        }).toList();
    }
}
