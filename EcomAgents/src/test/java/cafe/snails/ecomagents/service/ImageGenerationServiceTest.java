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

    private ImageGenerationService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ImageGenerationService(aiModelRepository, recordRepository, new ObjectMapper());
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

    // ===== saveImageBytes via private reflection =====

    @Test
    void saveImageBytes_shouldSaveToSubdirectory() throws Exception {
        byte[] imageData = "fake-png-data".getBytes();

        String resultPath = invokeSaveImageBytes(imageData, "generate");

        assertTrue(resultPath.endsWith(".png"), "文件名应以 .png 结尾: " + resultPath);
        Path fullPath = tempDir.resolve("generate").resolve(Path.of(resultPath).getFileName());
        assertTrue(Files.exists(fullPath), "文件应存在于磁盘: " + fullPath);
        assertArrayEquals(imageData, Files.readAllBytes(fullPath));
    }

    @Test
    void saveImageBytes_shouldCreateSubdirectoryIfNotExists() throws Exception {
        byte[] data = "data".getBytes();

        String resultPath = invokeSaveImageBytes(data, "edit");

        Path dir = tempDir.resolve("edit");
        assertTrue(Files.exists(dir), "子目录应被自动创建: " + dir);
        assertTrue(resultPath.endsWith(".png"));
    }

    @Test
    void saveImageBytes_shouldUseUniqueFileNames() throws Exception {
        byte[] data = "test".getBytes();

        String path1 = invokeSaveImageBytes(data, "generate");
        String path2 = invokeSaveImageBytes(data, "generate");

        assertNotEquals(path1, path2);
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

    private void invokeDownloadImage(String imageUrl, String subDir, String apiKey) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("downloadImage", String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(service, imageUrl, subDir, apiKey);
    }

    // ===== 反射辅助方法 =====

    @SuppressWarnings("unchecked")
    private String invokeSaveImageBytes(byte[] data, String subDir) throws Exception {
        var method = ImageGenerationService.class.getDeclaredMethod("saveImageBytes", byte[].class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, data, subDir);
    }
}
