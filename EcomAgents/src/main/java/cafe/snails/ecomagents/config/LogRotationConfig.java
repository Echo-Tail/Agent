package cafe.snails.ecomagents.config;

import cafe.snails.ecomagents.service.ClientLogFileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 客户端日志文件轮转与清理配置。
 * <p>
 * 每日凌晨 2:00 执行过期日志清理，清除超过保留天数的归档文件。
 * 保留天数通过 {@code client.log.retention-days} 配置，默认 30 天。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LogRotationConfig {

    private final ClientLogFileService clientLogFileService;

    /** 日志保留天数，默认 30 天。 */
    @Value("${client.log.retention-days:30}")
    private int retentionDays;

    /**
     * 应用启动时执行一次过期日志清理。
     */
    @PostConstruct
    public void init() {
        log.info("Client log retention: {} days, cleaning expired logs on startup...", retentionDays);
        clientLogFileService.cleanExpiredLogs(retentionDays);
    }

    /**
     * 定时清理过期日志文件。
     * 每天凌晨 2:00 执行一次（cron = "0 0 2 * * ?"）。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        log.debug("Scheduled cleanup of expired client logs (retention: {} days)", retentionDays);
        clientLogFileService.cleanExpiredLogs(retentionDays);
    }
}
