package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ClientLogRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 前端客户端日志文件写入服务。
 * <p>
 * 接收来自前端的批量日志，写入本地文件。
 * 日志文件按天归档，单文件超过 5MB 自动切分。
 * </p>
 *
 * <pre>
 * /logs/client/
 *   ├── client-2026-07-07.log        # 当日日志
 *   ├── client-2026-07-06.log        # 昨日归档
 *   ├── client-2026-07-06.1.log      # 超 5MB 切分
 *   └── client-2026-07-05.log        # 更早归档
 * </pre>
 */
@Slf4j
@Service
public class ClientLogFileService {

    /** 单日志文件最大字节数（5MB）。 */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    /** 日志文件日期格式。 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${client.log.directory:./logs/client}")
    private String logDirectory;

    /** 当前日志文件路径。 */
    private Path currentLogPath;

    /** 当前日志文件写入流。 */
    private OutputStreamWriter currentWriter;

    /** 当前日志日期。 */
    private LocalDate currentDate;

    /**
     * 初始化：确保日志目录存在。
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Path.of(logDirectory));
        } catch (IOException e) {
            log.error("Failed to create client log directory: {}", logDirectory, e);
        }
        rotateIfNeeded();
    }

    /**
     * 销毁时关闭当前文件流。
     */
    @PreDestroy
    public void destroy() {
        closeWriter();
    }

    /**
     * 写入一批前端日志。
     *
     * @param request 包含日志条目的请求体
     */
    public synchronized void writeBatch(ClientLogRequest request) {
        if (request == null || request.getLogs() == null || request.getLogs().isEmpty()) {
            return;
        }

        try {
            rotateIfNeeded();
            if (currentWriter == null) {
                log.warn("Client log writer not available, dropping {} log entries", request.getLogs().size());
                return;
            }

            for (ClientLogRequest.LogEntry entry : request.getLogs()) {
                StringBuilder line = new StringBuilder();
                line.append('[').append(entry.getTimestamp() != null ? entry.getTimestamp() : "unknown").append(']');
                line.append(" [").append(entry.getLevel() != null ? entry.getLevel() : "INFO").append(']');
                line.append(" [").append(entry.getContext() != null ? entry.getContext() : "-").append(']');
                line.append(' ').append(entry.getMessage() != null ? entry.getMessage() : "");

                if (entry.getData() != null && !entry.getData().isEmpty()) {
                    line.append(" | data=").append(entry.getData());
                }

                line.append('\n');
                currentWriter.write(line.toString());

                // 逐条检查文件大小，超限立即切分
                if (currentLogPath != null && Files.size(currentLogPath) >= MAX_FILE_SIZE) {
                    currentWriter.flush();
                    rotateFile();
                }
            }
            currentWriter.flush();
        } catch (IOException e) {
            log.error("Failed to write client log batch of {} entries", request.getLogs().size(), e);
        }
    }

    /**
     * 检查是否需要按天切分。
     * 跨天时自动关闭旧文件、打开新文件。
     */
    private void rotateIfNeeded() {
        LocalDate today = LocalDate.now();
        if (currentDate == null || !currentDate.equals(today)) {
            closeWriter();
            currentDate = today;
            openNewFile();
        }
    }

    /**
     * 当前文件超 5MB 时执行切分：当前文件重命名为 .N 后缀，新建文件。
     */
    private void rotateFile() {
        if (currentLogPath == null) return;
        closeWriter();

        // 找下一个可用序号
        int seq = 1;
        File rotated;
        String dateStr = currentDate.format(DATE_FMT);
        do {
            rotated = Path.of(logDirectory, "client-" + dateStr + "." + seq + ".log").toFile();
            seq++;
        } while (rotated.exists());

        try {
            Files.move(currentLogPath, rotated.toPath());
            log.info("Client log rotated: {} → {}", currentLogPath.getFileName(), rotated.getName());
        } catch (IOException e) {
            log.error("Failed to rotate client log file: {}", currentLogPath, e);
        }

        openNewFile();
    }

    /**
     * 打开当日日志文件（追加模式）。
     */
    private void openNewFile() {
        String dateStr = currentDate.format(DATE_FMT);
        currentLogPath = Path.of(logDirectory, "client-" + dateStr + ".log");
        try {
            currentWriter = new OutputStreamWriter(
                    new FileOutputStream(currentLogPath.toFile(), true),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            log.error("Failed to open client log file: {}", currentLogPath, e);
            currentWriter = null;
        }
    }

    /**
     * 关闭当前写入流。
     */
    private void closeWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                log.warn("Error closing client log writer", e);
            }
            currentWriter = null;
        }
    }

    /**
     * 清理超过保留天数的归档日志文件。
     * 保留天数由配置 {@code client.log.retention-days} 控制（默认 30）。
     *
     * @param retentionDays 日志保留天数
     */
    public void cleanExpiredLogs(int retentionDays) {
        File dir = new File(logDirectory);
        if (!dir.exists() || !dir.isDirectory()) return;

        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        File[] files = dir.listFiles((d, name) -> name.startsWith("client-") && name.endsWith(".log"));
        if (files == null) return;

        int deleted = 0;
        for (File file : files) {
            try {
                // 解析日期：client-2026-07-06.log 或 client-2026-07-06.1.log
                String name = file.getName();
                // 去掉 .log 后缀
                String baseName = name.endsWith(".log") ? name.substring(0, name.length() - 4) : name;
                // 去掉可能的 .N 序号
                String datePart = baseName.contains(".") ? baseName.substring(0, baseName.indexOf('.')) : baseName;
                // 去掉 "client-" 前缀
                String dateStr = datePart.startsWith("client-") ? datePart.substring(7) : datePart;

                LocalDate fileDate = LocalDate.parse(dateStr, DATE_FMT);
                if (fileDate.isBefore(cutoff)) {
                    Files.delete(file.toPath());
                    deleted++;
                }
            } catch (Exception e) {
                log.warn("Failed to process/delete expired log file: {}", file.getName(), e);
            }
        }

        if (deleted > 0) {
            log.info("Cleaned {} expired client log files older than {} days", deleted, retentionDays);
        }
    }
}
