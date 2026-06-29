package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.TokenUsageRecord;
import cafe.snails.ecomagents.repository.TokenUsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * Token 用量服务测试，验证调用记录、区间统计和费用换算。
 */
class TokenUsageServiceTest {

    @Mock
    private TokenUsageRecordRepository repository;

    private TokenUsageService service;

    @BeforeEach
    void setUp() {
        service = new TokenUsageService(repository);
        ReflectionTestUtils.setField(service, "usdCnyRate", new BigDecimal("7.00"));
    }

    @Test
    void record_shouldSetCreatedAtAndPersistRecord() {
        var record = TokenUsageRecord.builder().modelName("GPT-5.5").build();

        service.record(record);

        assertNotNull(record.getCreatedAt());
        verify(repository).save(record);
    }

    @Test
    void getSummaryByDateRange_shouldMapRowsAndCalculateKnownModelCost() {
        when(repository.sumByModelBetween(any(), any())).thenReturn(List.of(
                new Object[]{"Assistant", "GPT-5.5 Turbo", "TEXT", "alice", 2L, 3000L, 1000L, 2000L},
                new Object[]{"Assistant", "Unknown", "TEXT", "bob", null, null, null, null}
        ));

        var result = service.getSummaryByDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        assertEquals(2, result.size());
        assertEquals("Assistant", result.get(0).get("agentName"));
        assertEquals("GPT-5.5 Turbo", result.get(0).get("modelName"));
        assertEquals(2L, result.get(0).get("callCount"));
        assertEquals(3000L, result.get(0).get("totalTokens"));
        assertEquals(1000, result.get(0).get("promptTokens"));
        assertEquals(2000, result.get(0).get("completionTokens"));
        assertEquals(new BigDecimal("0.25"), result.get(0).get("cnyCost"));
        assertEquals(0L, result.get(1).get("callCount"));
        assertEquals(BigDecimal.ZERO.setScale(2), result.get(1).get("cnyCost"));

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).sumByModelBetween(startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDate.of(2026, 6, 1).atStartOfDay(), startCaptor.getValue());
        assertEquals(LocalDate.of(2026, 6, 2).atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    void getImageModelCallCount_shouldDelegateDateRangeToRepository() {
        when(repository.countImageModelCallsBetween(any(), any())).thenReturn(3L);

        var count = service.getImageModelCallCount(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        assertEquals(3L, count);
        verify(repository).countImageModelCallsBetween(
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                LocalDate.of(2026, 6, 2).atTime(LocalTime.MAX));
    }

    @Test
    void getDetailByDateRange_shouldMapRecordsAndDefaultNullTokenCounts() {
        var createdAt = LocalDateTime.of(2026, 6, 2, 12, 0);
        when(repository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(
                TokenUsageRecord.builder()
                        .id(10L)
                        .modelId(1L)
                        .modelName("DeepSeek V4 Flash")
                        .modelType("TEXT")
                        .userId(2L)
                        .agentId(3L)
                        .agentName("Agent")
                        .username("alice")
                        .promptTokens(null)
                        .completionTokens(500)
                        .totalTokens(500)
                        .success(false)
                        .errorMessage("timeout")
                        .createdAt(createdAt)
                        .build()
        ));

        var result = service.getDetailByDateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).get("id"));
        assertEquals("DeepSeek V4 Flash", result.get(0).get("modelName"));
        assertEquals(0, result.get(0).get("promptTokens"));
        assertEquals(500, result.get(0).get("completionTokens"));
        assertEquals(false, result.get(0).get("success"));
        assertEquals("timeout", result.get(0).get("errorMessage"));
        assertEquals(createdAt, result.get(0).get("createdAt"));
        assertEquals(new BigDecimal("0.00"), result.get(0).get("cnyCost"));
    }
}
