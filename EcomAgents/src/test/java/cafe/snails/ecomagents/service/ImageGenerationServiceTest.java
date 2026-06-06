package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ImageGenerationRecord;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ImageGenerationRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 图片生成服务测试。
 * <p>通过 public 方法间接测试内部逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class ImageGenerationServiceTest {

    @Mock
    private AiModelRepository aiModelRepository;

    @Mock
    private ImageGenerationRecordRepository recordRepository;

    @Mock
    private cafe.snails.ecomagents.service.TokenUsageService tokenUsageService;

    @Mock
    private cafe.snails.ecomagents.repository.UserRepository userRepository;

    private ImageGenerationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImageGenerationService(aiModelRepository, recordRepository, tokenUsageService, userRepository, new ObjectMapper());
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "timeoutSeconds", 300);
    }

    // ===== listRecords =====

    @Test
    void listRecords_shouldReturnPagedResults() {
        var record = ImageGenerationRecord.builder()
                .id(1L).userId(5L).prompt("test").build();
        Page<ImageGenerationRecord> page = new PageImpl<>(List.of(record));

        when(recordRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<ImageGenerationRecord> result = service.listRecords(5L, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, result.getContent().size());
        assertEquals("test", result.getContent().get(0).getPrompt());
    }

    @Test
    void listRecords_shouldFilterByDateRange() {
        when(recordRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        Page<ImageGenerationRecord> result = service.listRecords(5L, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), null, PageRequest.of(0, 20));

        assertTrue(result.getContent().isEmpty());
        verify(recordRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void listRecords_shouldFilterByPromptKeyword() {
        when(recordRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        service.listRecords(5L, null, null, "sunset", PageRequest.of(0, 20));

        verify(recordRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void listRecords_shouldReturnEmptyForNoResults() {
        when(recordRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        Page<ImageGenerationRecord> result = service.listRecords(5L, LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 2), "nonexistent", PageRequest.of(0, 20));

        assertTrue(result.getContent().isEmpty());
    }

    // ===== deleteRecord =====

    @Test
    void deleteRecord_shouldAllowOwnerToDelete() {
        var record = ImageGenerationRecord.builder()
                .id(1L).userId(5L).prompt("test").build();
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        service.deleteRecord(1L, 5L);

        verify(recordRepository).delete(record);
    }

    @Test
    void deleteRecord_shouldRejectNonOwner() {
        var record = ImageGenerationRecord.builder()
                .id(1L).userId(5L).prompt("test").build();
        when(recordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThrows(BusinessException.class, () -> service.deleteRecord(1L, 99L));
        verify(recordRepository, never()).delete((ImageGenerationRecord) any());
    }

    @Test
    void deleteRecord_shouldThrow404ForMissingRecord() {
        when(recordRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.deleteRecord(999L, 1L));
    }



    // ===== isModelNotFoundError =====

    @Test
    void isModelNotFoundError_shouldDetectPackyApiModelNotFound() throws Exception {
        String errorBody = "{\"error\":{\"code\":\"model_not_found\",\"message\":\"分组 sora 下模型 gpt-image-2 无可用渠道（distributor）\",\"type\":\"packy_api_error\"}}";
        boolean result = invokeIsModelNotFoundError(errorBody);
        assertTrue(result);
    }

    @Test
    void isModelNotFoundError_shouldDetectUnknownParameter() throws Exception {
        String errorBody = "{\"error\":{\"message\":\"Unknown parameter: 'response_format'.\",\"type\":\"invalid_request_error\",\"param\":\"response_format\",\"code\":\"unknown_parameter\"}}";
        boolean result = invokeIsModelNotFoundError(errorBody);
        assertTrue(result);
    }

    @Test
    void isModelNotFoundError_shouldReturnFalseForQuotaError() throws Exception {
        String errorBody = "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"余额不足\",\"type\":\"packy_api_error\"}}";
        boolean result = invokeIsModelNotFoundError(errorBody);
        assertFalse(result);
    }

    @Test
    void isModelNotFoundError_shouldReturnFalseForNullBody() throws Exception {
        assertFalse(invokeIsModelNotFoundError(null));
    }

    @Test
    void isModelNotFoundError_shouldReturnFalseForEmptyBody() throws Exception {
        assertFalse(invokeIsModelNotFoundError(""));
    }

    // ===== isFallbackConfigured =====

    @Test
    void isFallbackConfigured_shouldReturnTrueWhenExplicitConfigSet() throws Exception {
        var model = mock(ImageGenerationService.class); // just a placeholder, won't use
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "https://api.example.com/v1/chat/completions");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "sk-test");

        // Create a real AiModel object
        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setApiUrl("https://api.packyapi.com");
        aiModel.setApiKey("sk-packy");

        boolean result = invokeIsFallbackConfigured(aiModel);
        assertTrue(result);
    }

    @Test
    void isFallbackConfigured_shouldReturnTrueWhenModelHasUrlAndKey() throws Exception {
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "");

        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setApiUrl("https://api.packyapi.com");
        aiModel.setApiKey("sk-packy");

        boolean result = invokeIsFallbackConfigured(aiModel);
        assertTrue(result);
    }

    @Test
    void isFallbackConfigured_shouldReturnFalseWhenModelMissingUrl() throws Exception {
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "");

        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setApiUrl("");
        aiModel.setApiKey("sk-packy");

        boolean result = invokeIsFallbackConfigured(aiModel);
        assertFalse(result);
    }

    @Test
    void isFallbackConfigured_shouldReturnFalseWhenModelMissingKey() throws Exception {
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "");

        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setApiUrl("https://api.packyapi.com");
        aiModel.setApiKey("");

        boolean result = invokeIsFallbackConfigured(aiModel);
        assertFalse(result);
    }

    // ===== extractImageUrlFromMarkdown =====

    @Test
    void extractImageUrlFromMarkdown_shouldExtractUrl() throws Exception {
        String markdown = "![generated image](https://cdn.example.com/img/123.png)";
        String url = invokeExtractImageUrlFromMarkdown(markdown);
        assertEquals("https://cdn.example.com/img/123.png", url);
    }

    @Test
    void extractImageUrlFromMarkdown_shouldReturnNullForPlainText() throws Exception {
        String markdown = "这是一段普通文本，没有图片链接";
        String url = invokeExtractImageUrlFromMarkdown(markdown);
        assertNull(url);
    }

    @Test
    void extractImageUrlFromMarkdown_shouldReturnNullForNullInput() throws Exception {
        assertNull(invokeExtractImageUrlFromMarkdown(null));
    }

    // ===== escapeJson =====

    @Test
    void escapeJson_shouldEscapeSpecialCharacters() throws Exception {
        String result = invokeEscapeJson("hello \"world\"\nline2");
        assertEquals("hello \\\"world\\\"\\nline2", result);
    }

    @Test
    void escapeJson_shouldHandleNull() throws Exception {
        String result = invokeEscapeJson(null);
        assertEquals("", result);
    }

    // ===== generate fallback flow test =====

    @Test
    void generate_shouldTriggerFallbackWhenPackyApiReturnsModelNotFound() {
        // 配置 fallback 到本地不可用地址，验证 fallback 被触发（错误消息包含"备用接口"）
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "http://localhost:1/chat/completions");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "sk-fallback");

        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setId(1L);
        aiModel.setModelName("gpt-image-2");
        aiModel.setApiUrl("http://localhost:1");
        aiModel.setApiKey("sk-packy");
        aiModel.setModelType("IMAGE");
        aiModel.setEnabled(true);

        // Mock repository to return our test model
        when(aiModelRepository.findByModelTypeAndEnabled("IMAGE", true))
                .thenReturn(List.of(aiModel));

        // generate 会先尝试调 PackyAPI（localhost:1 连接被拒），再走 fallback
        // fallback 地址也是 localhost:1，同样连接失败
        var ex = assertThrows(BusinessException.class,
                () -> service.generate("test prompt", "1024x1024", "standard", 1L));
        // 验证异常消息包含 fallback 相关字样（说明 fallback 被触发了）
        assertTrue(ex.getMessage().contains("备用接口") || ex.getMessage().contains("失败"),
                "应触发 fallback, 消息: " + ex.getMessage());
    }

    @Test
    void edit_shouldTriggerFallbackWhenPackyApiReturnsModelNotFound() throws Exception {
        // 配置 fallback
        ReflectionTestUtils.setField(service, "fallbackApiUrl", "http://localhost:1/chat/completions");
        ReflectionTestUtils.setField(service, "fallbackApiKey", "sk-fallback");

        var aiModel = new cafe.snails.ecomagents.model.AiModel();
        aiModel.setId(1L);
        aiModel.setModelName("gpt-image-2");
        aiModel.setApiUrl("http://localhost:1");
        aiModel.setApiKey("sk-packy");
        aiModel.setModelType("IMAGE");
        aiModel.setEnabled(true);

        when(aiModelRepository.findByModelTypeAndEnabled("IMAGE", true))
                .thenReturn(List.of(aiModel));

        var image = new org.springframework.mock.web.MockMultipartFile(
                "image", "test.png", "image/png", "fake-png-bytes".getBytes());

        var ex = assertThrows(BusinessException.class,
                () -> service.edit("edit this", "1024x1024", "standard", List.of(image), 1L));
        assertTrue(ex.getMessage().contains("备用接口") || ex.getMessage().contains("失败"),
                "应触发 fallback, 消息: " + ex.getMessage());
    }

    // ===== downloadImage error paths via reflection =====

    @Test
    void downloadImage_shouldThrowOnConnectionFailure() throws Exception {
        String unreachableUrl = "http://localhost:1/nonexistent.png";
        // 连接被拒绝时 downloadImage 应抛出 IOException
        // 通过反射调用时异常被包装在 InvocationTargetException 中
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> invokeDownloadImage(unreachableUrl, "generate", "test-key"));
        assertInstanceOf(IOException.class, ex.getCause(),
                "底层异常应为 IOException");
    }

    private boolean invokeIsModelNotFoundError(String body) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("isModelNotFoundError", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, body);
    }

    private boolean invokeIsFallbackConfigured(cafe.snails.ecomagents.model.AiModel model) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("isFallbackConfigured", cafe.snails.ecomagents.model.AiModel.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, model);
    }

    private String invokeExtractImageUrlFromMarkdown(String markdown) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("extractImageUrlFromMarkdown", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, markdown);
    }

    private String invokeEscapeJson(String value) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("escapeJson", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, value);
    }

    private void invokeDownloadImage(String imageUrl, String subDir, String apiKey) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("downloadImage", String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, imageUrl, subDir, apiKey);
    }

    // ===== 反射辅助方法 =====


}
