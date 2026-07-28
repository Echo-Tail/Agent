package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.BrightDataConfig;
import cafe.snails.ecomagents.dto.*;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.BrightDataRecord;
import cafe.snails.ecomagents.repository.BrightDataRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bright Data 服务层单元测试。
 * <p>BrightDataConfig 读取自 application.properties，不硬编码测试参数。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrightDataServiceTest {

    @Mock private BrightDataRecordRepository recordRepository;
    @Mock private WebClient.Builder webClientBuilder;

    @Mock private WebClient webClient;
    @Mock private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private WebClient.RequestBodySpec requestBodySpec;
    @Mock private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;
    @Mock private Mono<String> mono;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BrightDataService service;

    /** 从 application.properties 加载的 BrightDataConfig */
    private static BrightDataConfig realConfig;

    /** 测试用 ASIN */
    private static final String TEST_ASIN = "B0TEST1234";
    private static final String TEST_AMAZON_URL = "https://www.amazon.com/dp/" + TEST_ASIN;
    private static final Long TEST_USER_ID = 1L;

    @BeforeAll
    static void loadConfig() throws IOException {
        // 从 application.properties 读取真实配置
        Properties props = new Properties();
        props.load(new ClassPathResource("application.properties").getInputStream());

        realConfig = new BrightDataConfig();
        realConfig.setApiKey(props.getProperty("brightdata.api-key", ""));
        realConfig.setBaseUrl(props.getProperty("brightdata.base-url", "https://api.brightdata.com"));
        realConfig.setDefaultDatasetId(props.getProperty("brightdata.default-dataset-id", ""));
    }

    @BeforeEach
    void setUp() {
        service = new BrightDataService(realConfig, recordRepository, webClientBuilder, objectMapper);

        // save() 返回被保存的实体并设置 ID
        lenient().doAnswer(invocation -> {
            BrightDataRecord r = invocation.getArgument(0);
            if (r.getId() == null) {
                var idField = BrightDataRecord.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(r, 42L);
            }
            return r;
        }).when(recordRepository).save(any(BrightDataRecord.class));
    }

    // ==================== scrape — 同步抓取 ====================

    @Test
    void scrape_shouldUseConfigValuesForRequestUrl() throws Exception {
        String responseJson = objectMapper.writeValueAsString(List.of(
                Map.of("url", TEST_AMAZON_URL, "title", "Test Product")
        ));
        mockPostChain(responseJson);

        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", TEST_AMAZON_URL)));
        req.setFormat("json");

        service.scrape(req, TEST_USER_ID);

        // 验证 URL 使用了配置文件中的 baseUrl + default-dataset-id
        verify(requestBodyUriSpec).uri(contains(realConfig.getBaseUrl()));
        verify(requestBodyUriSpec).uri(contains("/datasets/v3/scrape"));
        verify(requestBodyUriSpec).uri(contains("dataset_id=" + realConfig.getDefaultDatasetId()));
        // 验证 Authorization header 使用了配置文件中的 apiKey
        verify(requestBodySpec).header("Authorization", "Bearer " + realConfig.getApiKey());
    }

    @Test
    void scrape_shouldReturnRecordsOn200() throws Exception {
        String responseJson = objectMapper.writeValueAsString(List.of(
                Map.of("url", TEST_AMAZON_URL, "title", "Test Product", "price", "$29.99")
        ));
        mockPostChain(responseJson);

        ApiResponse<BrightDataScrapeResponse> result = service.scrape(makeScrapeRequest(), TEST_USER_ID);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getRecords().size());
        assertNull(result.getData().getSnapshotId());
        assertTrue(result.getData().getTimeCostMs() >= 0);
        verify(recordRepository).save(argThat(r ->
                "scrape".equals(r.getType()) &&
                "success".equals(r.getStatus()) &&
                r.getAsinList().contains(TEST_ASIN) &&
                realConfig.getDefaultDatasetId().equals(r.getDatasetId())
        ));
    }

    @Test
    void scrape_shouldHandle202Timeout() throws Exception {
        String responseJson = objectMapper.writeValueAsString(Map.of(
                "snapshot_id", "s_test_snapshot",
                "message", "still in progress"
        ));
        mockPostChain(responseJson);

        ApiResponse<BrightDataScrapeResponse> result = service.scrape(makeScrapeRequest(), TEST_USER_ID);

        assertEquals(202, result.getCode());
        assertEquals("s_test_snapshot", result.getData().getSnapshotId());
        verify(recordRepository).save(argThat(r ->
                "running".equals(r.getStatus()) &&
                "s_test_snapshot".equals(r.getSnapshotId())
        ));
    }

    @Test
    void scrape_shouldHandleNetworkError() {
        mockPostException(new RuntimeException("Connection refused"));

        ApiResponse<BrightDataScrapeResponse> result = service.scrape(makeScrapeRequest(), TEST_USER_ID);

        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("Connection refused"));
        verify(recordRepository).save(argThat(r ->
                "failed".equals(r.getStatus()) &&
                r.getErrorMessage().contains("Connection refused")
        ));
    }

    @Test
    void scrape_shouldExtractAsinFromAmazonUrls() throws Exception {
        String responseJson = objectMapper.writeValueAsString(List.of(
                Map.of("url", TEST_AMAZON_URL, "title", "A"),
                Map.of("url", "https://www.amazon.com/product/B0ABC12345", "title", "B")
        ));
        mockPostChain(responseJson);

        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", TEST_AMAZON_URL), Map.of("url", "https://www.amazon.com/product/B0ABC12345")));

        ApiResponse<BrightDataScrapeResponse> result = service.scrape(req, TEST_USER_ID);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().getRecords().size());
        verify(recordRepository).save(argThat(r ->
                r.getAsinList().contains(TEST_ASIN) && r.getAsinList().contains("B0ABC12345")
        ));
    }

    @Test
    void scrape_shouldUseDefaultDatasetIdWhenRequestOmitsIt() throws Exception {
        String responseJson = objectMapper.writeValueAsString(List.of(
                Map.of("url", TEST_AMAZON_URL, "title", "Product")
        ));
        mockPostChain(responseJson);

        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", TEST_AMAZON_URL)));
        req.setFormat("json");
        // 不传 datasetId → 应回退到配置文件中的默认值

        ApiResponse<BrightDataScrapeResponse> result = service.scrape(req, TEST_USER_ID);

        assertEquals(200, result.getCode());
        verify(requestBodyUriSpec).uri(contains("dataset_id=" + realConfig.getDefaultDatasetId()));
        verify(recordRepository).save(argThat(r ->
                realConfig.getDefaultDatasetId().equals(r.getDatasetId())
        ));
    }

    @Test
    void scrape_shouldRejectEmptyDatasetId() {
        // 临时清空默认值，模拟未配置的情况
        realConfig.setDefaultDatasetId("");
        assertThrows(BusinessException.class, () -> service.scrape(makeScrapeRequest(), TEST_USER_ID));
        // 恢复
        realConfig.setDefaultDatasetId(loadDefaultDatasetIdFromProps());
    }

    // ==================== trigger — 异步触发 ====================

    @Test
    void trigger_shouldUseConfigValuesForRequestUrl() throws Exception {
        String responseJson = objectMapper.writeValueAsString(Map.of("snapshot_id", "s_trigger_test"));
        mockPostChain(responseJson);

        service.trigger(makeTriggerRequest(), TEST_USER_ID);

        // 验证 URL 使用了配置文件中的 baseUrl + default-dataset-id
        verify(requestBodyUriSpec).uri(contains(realConfig.getBaseUrl()));
        verify(requestBodyUriSpec).uri(contains("/datasets/v3/trigger"));
        verify(requestBodyUriSpec).uri(contains("dataset_id=" + realConfig.getDefaultDatasetId()));
        // 验证 Authorization header 使用了配置文件中的 apiKey
        verify(requestBodySpec).header("Authorization", "Bearer " + realConfig.getApiKey());
    }

    @Test
    void trigger_shouldReturnSnapshotIdOnSuccess() throws Exception {
        String responseJson = objectMapper.writeValueAsString(Map.of("snapshot_id", "s_trigger_test"));
        mockPostChain(responseJson);

        ApiResponse<BrightDataTriggerResponse> result = service.trigger(makeTriggerRequest(), TEST_USER_ID);

        assertEquals(200, result.getCode());
        assertEquals("s_trigger_test", result.getData().getSnapshotId());
        assertEquals(realConfig.getDefaultDatasetId(), result.getData().getDatasetId());
        verify(recordRepository).save(argThat(r ->
                "trigger".equals(r.getType()) &&
                "running".equals(r.getStatus()) &&
                "s_trigger_test".equals(r.getSnapshotId())
        ));
    }

    @Test
    void trigger_shouldHandleNetworkError() {
        mockPostException(new RuntimeException("API rate limit exceeded"));

        ApiResponse<BrightDataTriggerResponse> result = service.trigger(makeTriggerRequest(), TEST_USER_ID);

        assertEquals(500, result.getCode());
        assertTrue(result.getMessage().contains("rate limit"));
        verify(recordRepository).save(argThat(r ->
                "failed".equals(r.getStatus()) && r.getErrorMessage().contains("rate limit")
        ));
    }

    // ==================== getProgress — 轮询进度 ====================

    @Test
    void getProgress_shouldUseConfigValuesForRequestUrl() throws Exception {
        mockGetChain("{\"snapshot_id\":\"s_test\",\"status\":\"running\"}");

        service.getProgress("s_test");

        verify(requestHeadersUriSpec).uri(contains(realConfig.getBaseUrl()));
        verify(requestHeadersUriSpec).uri(contains("/datasets/v3/progress/s_test"));
    }

    @Test
    void getProgress_shouldReturnRunningStatus() throws Exception {
        mockGetChain("{\"snapshot_id\":\"s_test\",\"status\":\"running\"}");

        ApiResponse<BrightDataSnapshotStatus> result = service.getProgress("s_test");

        assertEquals(200, result.getCode());
        assertEquals("running", result.getData().getStatus());
    }

    @Test
    void getProgress_whenReady_shouldUpdateLocalRecord() throws Exception {
        mockGetChain("{\"snapshot_id\":\"s_test\",\"status\":\"ready\"}");
        when(recordRepository.findBySnapshotId("s_test")).thenReturn(Optional.of(
                BrightDataRecord.builder().id(1L).snapshotId("s_test").status("running").build()
        ));

        ApiResponse<BrightDataSnapshotStatus> result = service.getProgress("s_test");

        assertEquals("ready", result.getData().getStatus());
        verify(recordRepository).save(argThat(r -> "ready".equals(r.getStatus())));
    }

    // ==================== downloadSnapshot — 下载结果 ====================

    @Test
    void downloadSnapshot_shouldUseConfigValuesForRequestUrl() throws Exception {
        mockGetChain("[{\"url\":\"" + TEST_AMAZON_URL + "\",\"title\":\"Downloaded\"}]");
        when(recordRepository.findBySnapshotId("s_test")).thenReturn(Optional.of(
                BrightDataRecord.builder().id(1L).snapshotId("s_test").build()
        ));

        service.downloadSnapshot("s_test", "json");

        verify(requestHeadersUriSpec).uri(contains(realConfig.getBaseUrl()));
        verify(requestHeadersUriSpec).uri(contains("/datasets/v3/snapshot/s_test"));
        verify(requestHeadersUriSpec).uri(contains("format=json"));
    }

    @Test
    void downloadSnapshot_shouldReturnData() throws Exception {
        mockGetChain("[{\"url\":\"" + TEST_AMAZON_URL + "\",\"title\":\"Downloaded\"}]");
        when(recordRepository.findBySnapshotId("s_test")).thenReturn(Optional.of(
                BrightDataRecord.builder().id(1L).snapshotId("s_test").build()
        ));

        ApiResponse<Object> result = service.downloadSnapshot("s_test", "json");

        assertEquals(200, result.getCode());
        assertInstanceOf(List.class, result.getData());
        verify(recordRepository).save(argThat(r -> "ready".equals(r.getStatus())));
    }

    // ==================== cancelSnapshot — 取消快照 ====================

    @Test
    void cancelSnapshot_shouldCallApiAndUpdateRecord() {
        mockPostForCancel();
        when(recordRepository.findBySnapshotId("s_test")).thenReturn(Optional.of(
                BrightDataRecord.builder().id(1L).snapshotId("s_test").status("running").build()
        ));

        ApiResponse<Void> result = service.cancelSnapshot("s_test");

        assertEquals(200, result.getCode());
        verify(recordRepository).save(argThat(r ->
                "failed".equals(r.getStatus()) && "用户取消".equals(r.getErrorMessage())
        ));
    }

    // ==================== listSnapshots — 列出快照 ====================

    @Test
    void listSnapshots_shouldUseConfigValuesForRequestUrl() throws Exception {
        mockGetChain("[{\"id\":\"s_1\",\"status\":\"ready\"}]");

        service.listSnapshots(null, null, 10);

        verify(requestHeadersUriSpec).uri(contains(realConfig.getBaseUrl()));
        verify(requestHeadersUriSpec).uri(contains("/datasets/v3/snapshots"));
        verify(requestHeadersUriSpec).uri(contains("dataset_id=" + realConfig.getDefaultDatasetId()));
    }

    @Test
    void listSnapshots_shouldReturnList() throws Exception {
        mockGetChain("[{\"id\":\"s_1\",\"status\":\"ready\"},{\"id\":\"s_2\",\"status\":\"running\"}]");

        ApiResponse<List<Map<String, Object>>> result = service.listSnapshots(realConfig.getDefaultDatasetId(), null, 10);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    // ==================== listRecords — 本地记录查询 ====================

    @Test
    void listRecords_shouldQueryByUserId() {
        when(recordRepository.findByUserIdOrderByCreatedAtDesc(eq(TEST_USER_ID), any()))
                .thenReturn(Page.empty());

        ApiResponse<Page<BrightDataRecord>> result = service.listRecords(TEST_USER_ID, 0, 10);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        verify(recordRepository).findByUserIdOrderByCreatedAtDesc(eq(TEST_USER_ID), any());
    }

    // ==================== 辅助 ====================

    private BrightDataScrapeRequest makeScrapeRequest() {
        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", TEST_AMAZON_URL)));
        req.setFormat("json");
        req.setIncludeErrors(true);
        return req;
    }

    private BrightDataTriggerRequest makeTriggerRequest() {
        BrightDataTriggerRequest req = new BrightDataTriggerRequest();
        req.setInput(List.of(Map.of("url", TEST_AMAZON_URL)));
        req.setFormat("json");
        req.setIncludeErrors(true);
        return req;
    }

    /** 从配置文件重新加载 defaultDatasetId */
    private String loadDefaultDatasetIdFromProps() {
        try {
            Properties props = new Properties();
            props.load(new ClassPathResource("application.properties").getInputStream());
            return props.getProperty("brightdata.default-dataset-id", "");
        } catch (IOException e) {
            return "";
        }
    }

    // ==================== WebClient Mock 链 ====================

    private void mockPostChain(String responseBody) {
        when(webClientBuilder.build()).thenReturn(webClient);
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn((WebClient.RequestBodySpec) requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(mono);
        when(mono.timeout(any(Duration.class))).thenReturn(mono);
        when(mono.block()).thenReturn(responseBody);
    }

    private void mockPostException(RuntimeException ex) {
        when(webClientBuilder.build()).thenReturn(webClient);
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn((WebClient.RequestBodySpec) requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(mono);
        when(mono.timeout(any(Duration.class))).thenReturn(mono);
        when(mono.block()).thenThrow(ex);
    }

    private void mockPostForCancel() {
        when(webClientBuilder.build()).thenReturn(webClient);
        doReturn(requestBodyUriSpec).when(webClient).post();
        doReturn((WebClient.RequestBodySpec) requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestBodySpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(mono);
        when(mono.timeout(any(Duration.class))).thenReturn(mono);
        when(mono.block()).thenReturn("OK");
    }

    @SuppressWarnings({"rawtypes"})
    private void mockGetChain(String responseBody) {
        when(webClientBuilder.build()).thenReturn(webClient);
        doReturn(requestHeadersUriSpec).when(webClient).get();
        doReturn((WebClient.RequestHeadersSpec) requestHeadersSpec).when(requestHeadersUriSpec).uri(anyString());
        doReturn(requestHeadersSpec).when(requestHeadersSpec).header(anyString(), anyString());
        doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        when(responseSpec.bodyToMono(String.class)).thenReturn(mono);
        when(mono.timeout(any(Duration.class))).thenReturn(mono);
        when(mono.block()).thenReturn(responseBody);
    }
}
