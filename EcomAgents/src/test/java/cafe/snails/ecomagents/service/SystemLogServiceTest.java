package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.SystemLog;
import cafe.snails.ecomagents.repository.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock
    private SystemLogRepository systemLogRepository;

    private SystemLogService systemLogService;

    @Captor
    private ArgumentCaptor<SystemLog> logCaptor;

    @Captor
    private ArgumentCaptor<Specification<SystemLog>> specCaptor;

    @BeforeEach
    void setUp() {
        systemLogService = new SystemLogService(systemLogRepository);
    }

    @Test
    void writeLog_shouldSaveAndReturn() {
        when(systemLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = systemLogService.writeLog("INFO", "API", "test message", null, null, "/test", 1L);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("INFO", result.getData().getLevel());
        assertEquals("API", result.getData().getCategory());
        assertEquals("test message", result.getData().getMessage());
        assertEquals("/test", result.getData().getRoute());
        assertEquals(1L, result.getData().getUserId());
        assertNotNull(result.getData().getCreatedAt());

        verify(systemLogRepository).save(logCaptor.capture());
        assertEquals("INFO", logCaptor.getValue().getLevel());
    }

    @Test
    void writeLog_shouldHandleAllFields() {
        when(systemLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = systemLogService.writeLog(
                "ERROR", "USER_ACTION", "error occurred",
                "{\"detail\":\"timeout\"}", 1500L, "/admin", 2L);

        assertEquals(200, result.getCode());
        assertEquals("ERROR", result.getData().getLevel());
        assertEquals("USER_ACTION", result.getData().getCategory());
        assertEquals("{\"detail\":\"timeout\"}", result.getData().getData());
        assertEquals(1500L, result.getData().getDuration());
    }

    @Test
    void queryLogs_shouldReturnPageWithoutFilters() {
        var log = SystemLog.builder().id(1L).level("INFO").message("test").createdAt(LocalDateTime.now()).build();
        Page<SystemLog> page = new PageImpl<>(List.of(log));

        when(systemLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        var result = systemLogService.queryLogs(null, null, null, null, null, 0, 20);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getContent().size());
        assertEquals(1L, result.getData().getContent().get(0).getId());

        verify(systemLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void queryLogs_shouldApplyLevelFilter() {
        when(systemLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        systemLogService.queryLogs(List.of("ERROR", "WARN"), null, null, null, null, 0, 20);

        verify(systemLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertNotNull(specCaptor.getValue());
    }

    @Test
    void queryLogs_shouldApplySearchFilter() {
        when(systemLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        systemLogService.queryLogs(null, null, null, null, "timeout", 0, 20);

        verify(systemLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertNotNull(specCaptor.getValue());
    }

    @Test
    void queryLogs_shouldApplyDateFilters() {
        when(systemLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();

        systemLogService.queryLogs(null, null, start, end, null, 0, 20);

        verify(systemLogRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertNotNull(specCaptor.getValue());
    }

    @Test
    void queryLogs_shouldPaginateCorrectly() {
        when(systemLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        systemLogService.queryLogs(null, null, null, null, null, 2, 10);

        verify(systemLogRepository).findAll(any(Specification.class), argThat((Pageable p) ->
                p.getPageNumber() == 2 && p.getPageSize() == 10));
    }

    @Test
    void getStats_shouldReturnAllMetrics() {
        when(systemLogRepository.count()).thenReturn(100L);
        when(systemLogRepository.countErrorsSince(any())).thenReturn(5L);
        when(systemLogRepository.countByCreatedAtAfter(any())).thenReturn(30L);
        when(systemLogRepository.countByLevel("DEBUG")).thenReturn(20L);
        when(systemLogRepository.countByLevel("INFO")).thenReturn(50L);
        when(systemLogRepository.countByLevel("WARN")).thenReturn(25L);
        when(systemLogRepository.countByLevel("ERROR")).thenReturn(5L);
        when(systemLogRepository.countByCategory("API")).thenReturn(40L);
        when(systemLogRepository.countByCategory("USER_ACTION")).thenReturn(30L);
        when(systemLogRepository.countByCategory("ROUTER")).thenReturn(10L);
        when(systemLogRepository.countByCategory("ERROR")).thenReturn(5L);
        when(systemLogRepository.countByCategory("PERFORMANCE")).thenReturn(10L);
        when(systemLogRepository.countByCategory("AUTH")).thenReturn(5L);
        when(systemLogRepository.countErrorsByHourNative(any())).thenReturn(List.of());

        var result = systemLogService.getStats();

        assertEquals(200, result.getCode());
        var stats = result.getData();
        assertEquals(100L, stats.get("total"));
        assertEquals(0.05, (Double) stats.get("errorRate"), 0.001);
        assertTrue(stats.containsKey("byLevel"));
        assertTrue(stats.containsKey("byCategory"));
        assertTrue(stats.containsKey("last24h"));
    }

    @Test
    void getStats_shouldHandleZeroTotal() {
        when(systemLogRepository.count()).thenReturn(0L);
        when(systemLogRepository.countErrorsSince(any())).thenReturn(0L);
        when(systemLogRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(systemLogRepository.countByLevel(anyString())).thenReturn(0L);
        when(systemLogRepository.countByCategory(anyString())).thenReturn(0L);
        when(systemLogRepository.countErrorsByHourNative(any())).thenReturn(List.of());

        var result = systemLogService.getStats();

        assertEquals(200, result.getCode());
        assertEquals(0.0, (Double) result.getData().get("errorRate"), 0.001);
    }

    @Test
    void clearLogs_shouldDeleteAll() {
        var result = systemLogService.clearLogs();
        assertEquals(200, result.getCode());
        verify(systemLogRepository).deleteAll();
    }
}
