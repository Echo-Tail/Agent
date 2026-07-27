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
import java.util.stream.Collectors;
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
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;

    private WebClient brightDataClient() {
        return webClientBuilder.clone()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

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
            String requestUrl = brightDataConfig.getBaseUrl() + "/datasets/v3/scrape?dataset_id=" + datasetId
                    + "&include_errors=" + (req.getIncludeErrors() != null ? req.getIncludeErrors() : true)
                    + "&format=" + (req.getFormat() != null ? req.getFormat() : "json")
                    + (req.getCustomOutputFields() != null ? "&custom_output_fields=" + req.getCustomOutputFields() : "");
            String bodyJson = toJson(body);
            String bodyPreview = bodyJson.length() > 500 ? bodyJson.substring(0, 500) + "..." : bodyJson;
            log.info("[BrightData] >>> scrape request: url={}, input={}, bodyPreview={}",
                    requestUrl,
                    req.getInput() != null ? req.getInput().stream().map(m -> m.get("url")).toList() : "[]",
                    bodyPreview);

            // 调用 Bright Data API
            String responseBody = brightDataClient()
                    .post()
                    .uri(requestUrl)
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

                log.info("[BrightData] scrape returned 202, wait 5s before polling, snapshotId={}, userId={}, costMs={}",
                        snapshotId, userId, costMs);

                try {
                    Thread.sleep(20_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }

                List<Map<String, Object>> records = pollSnapshot(snapshotId, record, req.getFormat(), start, userId);
                return ApiResponse.success(BrightDataScrapeResponse.builder()
                        .records(records)
                        .timeCostMs(System.currentTimeMillis() - start)
                        .recordId(record.getId())
                        .message("success (polled snapshot)")
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

            String responsePreview = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
            log.info("[BrightData] <<< scrape response: userId={}, records={}, costMs={}, responsePreview={}",
                    userId, records.size(), costMs, responsePreview);

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
            log.info("[BrightData] >>> trigger request: url={}, input={}",
                    urlBuilder, requestBodyJson.length() > 300 ? requestBodyJson.substring(0, 300) + "..." : requestBodyJson);

            // 调用 Bright Data API
            String responseBody = brightDataClient()
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
        log.info("[BrightData] >>> getProgress: snapshotId={}", snapshotId);
        try {
            String responseBody = brightDataClient()
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

            log.info("[BrightData] <<< getProgress: snapshotId={}, status={}",
                    snapshotId, status.getStatus());
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
        log.info("[BrightData] >>> downloadSnapshot: snapshotId={}, format={}", snapshotId, format);

        try {
            String fmt = (format != null) ? format : "json";
            String responseBody = brightDataClient()
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

            String resultPreview = result != null ? toJson(result).length() > 300 ?
                    toJson(result).substring(0, 300) + "..." : toJson(result) : "null";
            log.info("[BrightData] downloadSnapshot success, snapshotId={}, costMs={}, size={}",
                    snapshotId, costMs, resultPreview);

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
        log.info("[BrightData] >>> cancelSnapshot: snapshotId={}", snapshotId);
        try {
            brightDataClient()
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

    // ==================== 快照轮询 ====================

    /**
     * 轮询快照进度，ready 后自动下载数据。
     * <p>指数退避：2s → 4s → 8s → … → 30s，最多 30 次（约 5 分钟）。</p>
     *
     * @return 成功返回 records 列表，失败抛出 BusinessException
     */
    private List<Map<String, Object>> pollSnapshot(String snapshotId, BrightDataRecord record,
                                                     String format, long startTime, Long userId) {
        int maxRetries = 30;
        long pollInterval = 2000;
        long maxPollInterval = 30_000;
        String snapshotStatus = null;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }

            ApiResponse<BrightDataSnapshotStatus> progressResp = getProgress(snapshotId);
            if (progressResp.getCode() == 200 && progressResp.getData() != null) {
                snapshotStatus = progressResp.getData().getStatus();
                log.info("[BrightData] poll snapshotId={}, attempt={}, status={}",
                        snapshotId, attempt + 1, snapshotStatus);

                if ("ready".equals(snapshotStatus) || "failed".equals(snapshotStatus)) {
                    break;
                }
            }

            attempt++;
            pollInterval = Math.min(pollInterval * 2, maxPollInterval);
        }

        long costMs = System.currentTimeMillis() - startTime;

        if ("ready".equals(snapshotStatus)) {
            // 尝试下载快照（返回 List 才是真数据，String 说明还在 building，继续轮询）
            ApiResponse<Object> downloadResp = downloadSnapshot(snapshotId, format);
            if (downloadResp.getCode() == 200 && downloadResp.getData() instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) downloadResp.getData();

                record.setStatus("success");
                record.setTimeCostMs(costMs);
                record.setDatasetSize(records.size());
                record.setResultSummary(toJson(Map.of(
                        "recordCount", records.size(),
                        "preview", records.isEmpty() ? null : records.get(0)
                )));
                recordRepository.save(record);

                log.info("[BrightData] poll done, snapshotId={}, userId={}, records={}, costMs={}",
                        snapshotId, userId, records.size(), costMs);
                return records;
            }

            // 快照尚未完全就绪（如 "try again in 30s"），继续轮询
            if (downloadResp.getCode() == 200) {
                log.info("[BrightData] download not ready yet, continue polling, snapshotId={}", snapshotId);
            } else {
                log.warn("[BrightData] download failed (code={}), continue polling, snapshotId={}",
                        downloadResp.getCode(), snapshotId);
            }
        }

        if ("failed".equals(snapshotStatus)) {
            record.setStatus("failed");
            record.setTimeCostMs(costMs);
            record.setErrorMessage("snapshot processing failed on Bright Data side");
            recordRepository.save(record);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Bright Data 快照处理失败");
        }

        // 轮询超时
        record.setStatus("running");
        record.setTimeCostMs(costMs);
        record.setErrorMessage("polling timed out after " + maxRetries + " retries");
        recordRepository.save(record);
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "Bright Data 快照轮询超时, snapshotId=" + snapshotId);
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
            log.info("[BrightData] >>> listSnapshots: datasetId={}, status={}, limit={}", resolvedDatasetId, status, limit);

            String responseBody = brightDataClient()
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
     * 根据 ASIN 获取商品图片 URL 列表（优先查缓存）。
     */
    public List<String> getImageUrlsByAsin(String asin, Long userId) {
        // Check cache first
        BrightDataScrapeResponse cached = findRecentByAsin(asin, userId);
        if (cached != null && cached.getRecords() != null) {
            List<String> urls = new ArrayList<>();
            for (Map<String, Object> record : cached.getRecords()) {
                if (record.containsKey("images") && record.get("images") instanceof List) {
                    for (Object img : (List<?>) record.get("images")) {
                        if (img instanceof String s && !urls.contains(s)) urls.add(s);
                    }
                }
                if (record.containsKey("image_url") && record.get("image_url") instanceof String s
                        && !urls.contains(s)) urls.add(s);
                // A+ 图：product_description[].url
                if (record.containsKey("product_description") && record.get("product_description") instanceof List) {
                    for (Object item : (List<?>) record.get("product_description")) {
                        if (item instanceof Map<?, ?> m && m.get("url") instanceof String s
                                && !urls.contains(s)) urls.add(s);
                    }
                }
            }
            if (!urls.isEmpty()) return urls;
        }
        // Not cached, call Bright Data API
        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", "https://www.amazon.com/dp/" + asin)));
        ApiResponse<BrightDataScrapeResponse> res = scrape(req, userId);
        if (res.getCode() == 200 && res.getData() != null && res.getData().getRecords() != null) {
            List<String> urls = new ArrayList<>();
            for (Map<String, Object> record : res.getData().getRecords()) {
                if (record.containsKey("images") && record.get("images") instanceof List) {
                    for (Object img : (List<?>) record.get("images")) {
                        if (img instanceof String s && !urls.contains(s)) urls.add(s);
                    }
                }
                // A+ 图：product_description[].url
                if (record.containsKey("product_description") && record.get("product_description") instanceof List) {
                    for (Object item : (List<?>) record.get("product_description")) {
                        if (item instanceof Map<?, ?> m && m.get("url") instanceof String s
                                && !urls.contains(s)) urls.add(s);
                    }
                }
            }
            return urls;
        }
        return List.of();
    }

    /**
     * 查找最近对该 ASIN 的成功采集记录，避免重复调用 Bright Data API。
     * @return 缓存的 BrightDataScrapeResponse，如果无缓存返回 null
     */
    @SuppressWarnings("unchecked")
    public BrightDataScrapeResponse findRecentByAsin(String asin, Long userId) {
        try {
            var cached = recordRepository
                    .findTop3ByAsinListContainingAndStatusAndTypeOrderByCreatedAtDesc(asin, "success", "scrape");
            for (BrightDataRecord r : cached) {
                // Verify the ASIN is actually in this record's asinList
                if (r.getAsinList() != null && r.getAsinList().contains(asin)) {
                    if (r.getResultSummary() != null) {
                        // Reconstruct scrape response from cached result
                        var summary = objectMapper.readTree(r.getResultSummary());
                        List<Map<String, Object>> records = new ArrayList<>();
                        if (summary.has("preview") && !summary.get("preview").isNull()) {
                            records.add(objectMapper.convertValue(summary.get("preview"), Map.class));
                        }
                        log.info("[BrightData] cache hit for ASIN={}, recordId={}, createdAt={}",
                                asin, r.getId(), r.getCreatedAt());
                        return BrightDataScrapeResponse.builder()
                                .records(records)
                                .timeCostMs(r.getTimeCostMs())
                                .recordId(r.getId())
                                .message("cached (record #" + r.getId() + ")")
                                .build();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[BrightData] cache lookup failed for ASIN={}: {}", asin, e.getMessage());
        }
        return null;
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
