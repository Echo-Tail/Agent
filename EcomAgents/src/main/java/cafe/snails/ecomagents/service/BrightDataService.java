package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.BrightDataConfig;
import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.BrightDataRecord;
import cafe.snails.ecomagents.repository.BrightDataRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bright Data Web Scraper API 服务层。
 * <p>封装同步/异步抓取、进度查询、结果下载等操作，自动记录调用日志到数据库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrightDataService {

    private final BrightDataConfig brightDataConfig;
    private final BrightDataRecordRepository recordRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    /** ASIN 提取正则：Amazon 产品 ID 通常为 10 位字母数字 */
    private static final Pattern ASIN_PATTERN = Pattern.compile(
            "/(?:dp|product|gp/product)/([A-Z0-9]{10})",
            Pattern.CASE_INSENSITIVE
    );

    /** 默认请求超时 60 秒（同步 scrape 本身有 1 分钟限制） */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** 监控进度请求超时 15 秒 */
    private static final Duration PROGRESS_TIMEOUT = Duration.ofSeconds(15);

    // ==================== 公开方法 ====================

    /**
     * 同步抓取：调用 Bright Data POST /datasets/v3/scrape，解析 JSON 返回。
     * 超时（202）时返回 snapshot_id，由前端决定后续轮询。
     */
    @Transactional
    public ApiResponse<BrightDataScrapeResponse> scrape(BrightDataScrapeRequest req, Long userId) {
        long start = System.currentTimeMillis();
        String datasetId = resolveDatasetId(req.getDatasetId());

        // 构建请求 body（BD 的 body 就是 input 数组 + 可选的 custom_output_fields）
        Map<String, Object> body = buildScrapeBody(req);

        // 构建 DB 记录
        BrightDataRecord record = createBaseRecord(userId, "scrape", datasetId, req.getInput());
        record.setRequestParams(toJson(body));

        try {
            // 调用 Bright Data API
            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(brightDataConfig.getBaseUrl() + "/datasets/v3/scrape?dataset_id=" + datasetId
                            + "&include_errors=" + (req.getIncludeErrors() != null ? req.getIncludeErrors() : true)
                            + "&format=" + (req.getFormat() != null ? req.getFormat() : "json")
                            + (req.getCustomOutputFields() != null ? "&custom_output_fields=" + req.getCustomOutputFields() : ""))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            long costMs = System.currentTimeMillis() - start;

            // 尝试解析为 JSON —— 如果是 202 超时降级，响应是 {"snapshot_id":"s_xxx","message":"..."}
            Map<String, Object> responseMap = tryParseJsonMap(responseBody);
            if (responseMap != null && responseMap.containsKey("snapshot_id")) {
                // 202 超时降级
                String snapshotId = (String) responseMap.get("snapshot_id");
                record.setSnapshotId(snapshotId);
                record.setStatus("running");
                record.setTimeCostMs(costMs);
                record.setResultSummary(toJson(responseMap));
                record = recordRepository.save(record);

                log.info("[BrightData] scrape timeout (202), snapshotId={}, userId={}, costMs={}",
                        snapshotId, userId, costMs);

                return new ApiResponse<>(202, "请求超时，请通过 snapshotId 轮询结果",
                        BrightDataScrapeResponse.builder()
                                .snapshotId(snapshotId)
                                .timeCostMs(costMs)
                                .recordId(record.getId())
                                .message("Your request is still in progress. Use the snapshot_id to poll via progress endpoint.")
                                .build());
            }

            // 正常 200 响应 —— 解析为 JSON 数组
            List<Map<String, Object>> records = parseJsonArray(responseBody);

            // 更新 DB 记录
            record.setStatus("success");
            record.setTimeCostMs(costMs);
            record.setDatasetSize(records.size());
            record.setResultSummary(toJson(Map.of(
                    "recordCount", records.size(),
                    "preview", records.size() > 0 ? records.get(0) : null
            )));
            record = recordRepository.save(record);

            log.info("[BrightData] scrape success, userId={}, records={}, costMs={}",
                    userId, records.size(), costMs);

            return ApiResponse.success(BrightDataScrapeResponse.builder()
                    .records(records)
                    .timeCostMs(costMs)
                    .recordId(record.getId())
                    .message("success")
                    .build());

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            record.setStatus("failed");
            record.setTimeCostMs(costMs);
            record.setErrorMessage(e.getMessage());
            recordRepository.save(record);

            log.error("[BrightData] scrape failed, userId={}, costMs={}, error={}",
                    userId, costMs, e.getMessage(), e);

            return ApiResponse.error(500, "Bright Data 同步抓取失败: " + e.getMessage());
        }
    }

    /**
     * 异步触发：调用 Bright Data POST /datasets/v3/trigger，返回 snapshot_id。
     */
    @Transactional
    public ApiResponse<BrightDataTriggerResponse> trigger(BrightDataTriggerRequest req, Long userId) {
        long start = System.currentTimeMillis();
        String datasetId = resolveDatasetId(req.getDatasetId());

        // 构建 requestParams（BD trigger 的 body 就是 input 数组）
        String requestBodyJson = toJson(req.getInput());

        // 构建 DB 记录
        BrightDataRecord record = createBaseRecord(userId, "trigger", datasetId, req.getInput());
        record.setRequestParams(requestBodyJson);

        try {
            // 构建 URL query 参数
            StringBuilder urlBuilder = new StringBuilder(
                    brightDataConfig.getBaseUrl() + "/datasets/v3/trigger?dataset_id=" + datasetId);
            urlBuilder.append("&include_errors=").append(req.getIncludeErrors() != null ? req.getIncludeErrors() : true);
            urlBuilder.append("&format=").append(req.getFormat() != null ? req.getFormat() : "json");
            if (req.getCustomOutputFields() != null) {
                urlBuilder.append("&custom_output_fields=").append(req.getCustomOutputFields());
            }
            if (req.getType() != null) {
                urlBuilder.append("&type=").append(req.getType());
            }
            if (req.getDiscoverBy() != null) {
                urlBuilder.append("&discover_by=").append(req.getDiscoverBy());
            }
            if (req.getLimitPerInput() != null) {
                urlBuilder.append("&limit_per_input=").append(req.getLimitPerInput());
            }
            if (req.getLimitMultipleResults() != null) {
                urlBuilder.append("&limit_multiple_results=").append(req.getLimitMultipleResults());
            }
            if (req.getNotify() != null) {
                urlBuilder.append("&notify=").append(req.getNotify());
            }
            if (req.getEndpoint() != null) {
                urlBuilder.append("&endpoint=").append(req.getEndpoint());
            }

            // 调用 Bright Data API
            String responseBody = webClientBuilder.build()
                    .post()
                    .uri(urlBuilder.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req.getInput())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            long costMs = System.currentTimeMillis() - start;

            // 解析响应：{"snapshot_id": "s_xxx"}
            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
            String snapshotId = (String) responseMap.get("snapshot_id");

            record.setSnapshotId(snapshotId);
            record.setStatus("running");
            record.setTimeCostMs(costMs);
            record.setResultSummary(toJson(responseMap));
            record = recordRepository.save(record);

            log.info("[BrightData] trigger success, userId={}, snapshotId={}, costMs={}",
                    userId, snapshotId, costMs);

            return ApiResponse.success(BrightDataTriggerResponse.builder()
                    .snapshotId(snapshotId)
                    .datasetId(datasetId)
                    .recordId(record.getId())
                    .build());

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            record.setStatus("failed");
            record.setTimeCostMs(costMs);
            record.setErrorMessage(e.getMessage());
            recordRepository.save(record);

            log.error("[BrightData] trigger failed, userId={}, costMs={}, error={}",
                    userId, costMs, e.getMessage(), e);

            return ApiResponse.error(500, "Bright Data 异步触发失败: " + e.getMessage());
        }
    }

    /**
     * 查询快照进度：调用 Bright Data GET /datasets/v3/progress/{snapshotId}。
     */
    public ApiResponse<BrightDataSnapshotStatus> getProgress(String snapshotId) {
        try {
            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(brightDataConfig.getBaseUrl() + "/datasets/v3/progress/" + snapshotId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(PROGRESS_TIMEOUT)
                    .block();

            Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

            BrightDataSnapshotStatus status = BrightDataSnapshotStatus.builder()
                    .snapshotId((String) responseMap.get("snapshot_id"))
                    .datasetId((String) responseMap.get("dataset_id"))
                    .status((String) responseMap.get("status"))
                    .build();

            // 如果状态是 ready 或 failed，同步更新本地记录
            if ("ready".equals(status.getStatus()) || "failed".equals(status.getStatus())) {
                recordRepository.findBySnapshotId(snapshotId).ifPresent(record -> {
                    record.setStatus(status.getStatus());
                    recordRepository.save(record);
                });
            }

            return ApiResponse.success(status);

        } catch (Exception e) {
            log.error("[BrightData] getProgress failed, snapshotId={}, error={}", snapshotId, e.getMessage(), e);
            return ApiResponse.error(500, "查询进度失败: " + e.getMessage());
        }
    }

    /**
     * 下载快照数据：调用 Bright Data GET /datasets/v3/snapshot/{snapshotId}。
     */
    public ApiResponse<Object> downloadSnapshot(String snapshotId, String format) {
        long start = System.currentTimeMillis();

        try {
            String fmt = (format != null) ? format : "json";
            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(brightDataConfig.getBaseUrl() + "/datasets/v3/snapshot/" + snapshotId
                            + "?format=" + fmt)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            long costMs = System.currentTimeMillis() - start;

            // 尝试解析为 JSON，失败则返回原始字符串
            Object result;
            try {
                if ("json".equals(fmt)) {
                    result = objectMapper.readValue(responseBody, List.class);
                } else {
                    result = responseBody;
                }
            } catch (Exception e) {
                result = responseBody;
            }

            // 更新本地记录
            recordRepository.findBySnapshotId(snapshotId).ifPresent(record -> {
                record.setStatus("ready");
                record.setResultSummary(toJson(Map.of(
                        "downloaded", true,
                        "format", fmt
                )));
                recordRepository.save(record);
            });

            log.info("[BrightData] downloadSnapshot success, snapshotId={}, costMs={}", snapshotId, costMs);

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("[BrightData] downloadSnapshot failed, snapshotId={}, error={}",
                    snapshotId, e.getMessage(), e);
            return ApiResponse.error(500, "下载快照失败: " + e.getMessage());
        }
    }

    /**
     * 取消快照：调用 Bright Data POST /datasets/v3/snapshot/{snapshotId}/cancel。
     */
    public ApiResponse<Void> cancelSnapshot(String snapshotId) {
        try {
            webClientBuilder.build()
                    .post()
                    .uri(brightDataConfig.getBaseUrl() + "/datasets/v3/snapshot/" + snapshotId + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(PROGRESS_TIMEOUT)
                    .block();

            // 更新本地记录
            recordRepository.findBySnapshotId(snapshotId).ifPresent(record -> {
                record.setStatus("failed");
                record.setErrorMessage("用户取消");
                recordRepository.save(record);
            });

            log.info("[BrightData] cancelSnapshot success, snapshotId={}", snapshotId);
            return ApiResponse.success("已取消", null);

        } catch (Exception e) {
            log.error("[BrightData] cancelSnapshot failed, snapshotId={}, error={}",
                    snapshotId, e.getMessage(), e);
            return ApiResponse.error(500, "取消快照失败: " + e.getMessage());
        }
    }

    /**
     * 列出 Bright Data 侧历史快照。
     */
    @SuppressWarnings("unchecked")
    public ApiResponse<List<Map<String, Object>>> listSnapshots(String datasetId, String status, Integer limit) {
        try {
            String resolvedDatasetId = resolveDatasetId(datasetId);
            StringBuilder urlBuilder = new StringBuilder(
                    brightDataConfig.getBaseUrl() + "/datasets/v3/snapshots?dataset_id=" + resolvedDatasetId);
            if (status != null) {
                urlBuilder.append("&status=").append(status);
            }
            if (limit != null) {
                urlBuilder.append("&limit=").append(limit);
            }

            String responseBody = webClientBuilder.build()
                    .get()
                    .uri(urlBuilder.toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + brightDataConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(PROGRESS_TIMEOUT)
                    .block();

            List<Map<String, Object>> snapshots = objectMapper.readValue(responseBody, List.class);
            return ApiResponse.success(snapshots);

        } catch (Exception e) {
            log.error("[BrightData] listSnapshots failed, error={}", e.getMessage(), e);
            return ApiResponse.error(500, "列出快照失败: " + e.getMessage());
        }
    }

    /**
     * 查询本地数据库中的 Bright Data 调用记录。
     */
    public ApiResponse<Page<BrightDataRecord>> listRecords(Long userId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = recordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return ApiResponse.success(result);
    }

    // ==================== 内部方法 ====================

    /**
     * 解析 datasetId：优先使用请求中的值，否则回退到默认配置。
     */
    private String resolveDatasetId(String datasetId) {
        if (datasetId != null && !datasetId.isBlank()) {
            return datasetId;
        }
        if (brightDataConfig.getDefaultDatasetId() != null && !brightDataConfig.getDefaultDatasetId().isBlank()) {
            return brightDataConfig.getDefaultDatasetId();
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "datasetId 不能为空，请在请求中指定或配置 brightdata.default-dataset-id");
    }

    /**
     * 构建同步 scrape 的请求 body。
     */
    private Map<String, Object> buildScrapeBody(BrightDataScrapeRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", req.getInput());
        if (req.getCustomOutputFields() != null) {
            body.put("custom_output_fields", req.getCustomOutputFields());
        }
        return body;
    }

    /**
     * 创建基础 DB 记录（未保存，由调用方填充具体字段后保存）。
     */
    private BrightDataRecord createBaseRecord(Long userId, String type, String datasetId,
                                               List<Map<String, Object>> input) {
        BrightDataRecord record = new BrightDataRecord();
        record.setUserId(userId);
        record.setType(type);
        record.setDatasetId(datasetId);
        record.setStatus("running");
        record.setAsinList(extractAsins(input));
        return record;
    }

    /**
     * 从 input URL 列表中提取 Amazon ASIN。
     */
    private String extractAsins(List<Map<String, Object>> input) {
        if (input == null || input.isEmpty()) {
            return "[]";
        }
        List<String> asins = new ArrayList<>();
        for (Map<String, Object> item : input) {
            Object urlObj = item.get("url");
            if (urlObj instanceof String url) {
                Matcher matcher = ASIN_PATTERN.matcher(url);
                while (matcher.find()) {
                    asins.add(matcher.group(1).toUpperCase());
                }
            }
        }
        // 去重
        asins = asins.stream().distinct().collect(Collectors.toList());
        return toJson(asins);
    }

    /**
     * 尝试将字符串解析为 JSON Map，解析失败返回 null。
     */
    private Map<String, Object> tryParseJsonMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将字符串解析为 JSON 数组。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.warn("[BrightData] 响应非 JSON 数组格式: {}", json);
            return List.of();
        }
    }

    /**
     * 将对象序列化为 JSON 字符串，失败返回 "{}"。
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[BrightData] JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }
}
