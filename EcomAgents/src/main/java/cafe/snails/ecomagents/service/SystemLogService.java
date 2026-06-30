package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SystemLog;
import cafe.snails.ecomagents.repository.SystemLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 系统日志服务，负责日志写入、动态查询、统计聚合和清理。
 */
@Service
@RequiredArgsConstructor
public class SystemLogService {

    /** 系统日志仓库。 */
    private final SystemLogRepository systemLogRepository;

    /** 统计面板展示的固定日志级别集合。 */
    private static final List<String> LOG_LEVELS = List.of("DEBUG", "INFO", "WARN", "ERROR");
    /** 统计面板展示的固定日志类别集合。 */
    private static final List<String> LOG_CATEGORIES = List.of("API", "USER_ACTION", "ROUTER", "ERROR", "PERFORMANCE", "AUTH");

    /**
     * 写入一条系统日志。
     */
    @Transactional
    public ApiResponse<SystemLog> writeLog(String level, String category, String message,
                                           String data, Long duration, String route, Long userId) {
        SystemLog log = SystemLog.builder()
                .level(level)
                .category(category)
                .message(message)
                .data(data)
                .duration(duration)
                .route(route)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();
        return ApiResponse.success(systemLogRepository.save(log));
    }

    /**
     * 按级别、类别、时间范围和关键字动态分页查询日志。
     */
    public ApiResponse<Page<SystemLog>> queryLogs(List<String> levels, List<String> categories,
                                                   LocalDateTime startDate, LocalDateTime endDate,
                                                   String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<SystemLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (levels != null && !levels.isEmpty()) {
                predicates.add(root.get("level").in(levels));
            }
            if (categories != null && !categories.isEmpty()) {
                predicates.add(root.get("category").in(categories));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("message")), pattern.toLowerCase()),
                        cb.like(cb.lower(root.get("data")), pattern.toLowerCase())
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ApiResponse.success(systemLogRepository.findAll(spec, pageable));
    }

    /**
     * 汇总日志总量、级别/类别分布、错误率和最近 24 小时错误趋势。
     */
    public ApiResponse<Map<String, Object>> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);

        long total = systemLogRepository.count();
        long totalErrors = systemLogRepository.countErrorsSince(last24h);

        Map<String, Long> byLevel = new HashMap<>();
        for (String lv : LOG_LEVELS) {
            byLevel.put(lv, systemLogRepository.countByLevel(lv));
        }

        Map<String, Long> byCategory = new HashMap<>();
        for (String cat : LOG_CATEGORIES) {
            byCategory.put(cat, systemLogRepository.countByCategory(cat));
        }

        double errorRate = total > 0 ? (double) totalErrors / total : 0.0;

        List<Object[]> rows = systemLogRepository.countErrorsByHourNative(last24h);
        DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("HH:mm");
        List<Map<String, Object>> last24hTrend = new ArrayList<>();
        for (Object[] row : rows) {
            Object hourObj = row[0];
            LocalDateTime hourLdt;
            if (hourObj instanceof LocalDateTime) {
                hourLdt = (LocalDateTime) hourObj;
            } else if (hourObj instanceof java.sql.Timestamp) {
                hourLdt = ((java.sql.Timestamp) hourObj).toLocalDateTime();
            } else {
                continue;
            }
            long cnt = ((Number) row[1]).longValue();
            Map<String, Object> entry = new HashMap<>();
            entry.put("hour", hourLdt.format(hourFmt));
            entry.put("count", cnt);
            last24hTrend.add(entry);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("byLevel", byLevel);
        stats.put("byCategory", byCategory);
        stats.put("errorRate", errorRate);
        stats.put("last24h", last24hTrend);
        return ApiResponse.success(stats);
    }

    /**
     * 清空所有系统日志。
     */
    @Transactional
    public ApiResponse<Void> clearLogs() {
        systemLogRepository.deleteAll();
        return ApiResponse.success(null);
    }
}
