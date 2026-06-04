package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.repository.FileRecordRepository;
import cafe.snails.ecomagents.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 文件控制器测试，验证文件上传和文件元数据查询接口。
 */
class FileControllerTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileRecordRepository fileRecordRepository;

    @InjectMocks
    private FileController fileController;

    @Test
    void getFileRecord_shouldReturnFile() {
        var record = FileRecord.builder()
                .id(1L)
                .originalName("test.txt")
                .fileSize(13L)
                .build();

        when(fileStorageService.getFileRecord(1L))
                .thenReturn(ApiResponse.success(record));

        var result = fileController.getFileRecord(1L);
        assertEquals(200, result.getCode());
        assertEquals("test.txt", result.getData().getOriginalName());
    }

    @Test
    void getFileRecord_shouldReturn404() {
        when(fileStorageService.getFileRecord(999L))
                .thenReturn(ApiResponse.error(404, "文件不存在"));

        var result = fileController.getFileRecord(999L);
        assertEquals(404, result.getCode());
        assertEquals("文件不存在", result.getMessage());
    }

    @Test
    void uploadFile_withContext_shouldSaveContext() {
        // Given
        var file = mock(MultipartFile.class);
        var savedRecord = FileRecord.builder()
                .id(1L)
                .originalName("test.txt")
                .fileSize(13L)
                .build();

        when(fileStorageService.uploadFile(file, 1L))
                .thenReturn(ApiResponse.success(savedRecord));
        when(fileRecordRepository.save(any())).thenReturn(savedRecord);

        // When
        var result = fileController.uploadFile(file, "PRIVATE", 5L, 1L);

        // Then
        assertEquals(200, result.getCode());
        // Verify context was set and saved
        verify(fileRecordRepository).save(argThat(r ->
                "PRIVATE".equals(r.getContextType()) && 5L == r.getContextId()));
    }

    @Test
    void uploadFile_shouldReturnError_whenServiceFails() {
        var file = mock(MultipartFile.class);
        when(fileStorageService.uploadFile(file, 1L))
                .thenReturn(ApiResponse.error(500, "上传失败"));

        var result = fileController.uploadFile(file, "PRIVATE", 5L, 1L);

        assertEquals(500, result.getCode());
        assertEquals("上传失败", result.getMessage());
        // Context should NOT be saved when upload fails
        verify(fileRecordRepository, never()).save(any());
    }

    @Test
    void downloadFile_shouldReturn404_whenFileNotFound() {
        when(fileStorageService.getFileRecord(999L))
                .thenReturn(ApiResponse.error(404, "文件不存在"));

        var result = fileController.downloadFile(999L);

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void uploadFile_withoutContext_shouldNotSaveAgain() {
        var file = mock(MultipartFile.class);
        var savedRecord = FileRecord.builder()
                .id(1L)
                .originalName("test.txt")
                .fileSize(13L)
                .build();

        when(fileStorageService.uploadFile(file, 1L))
                .thenReturn(ApiResponse.success(savedRecord));

        var result = fileController.uploadFile(file, null, null, 1L);

        assertEquals(200, result.getCode());
        // Should NOT save context when contextType is null
        verify(fileRecordRepository, never()).save(any());
    }

    @Test
    void listMyFiles_shouldFilterByContext() {
        var files = List.of(
                FileRecord.builder().id(1L).originalName("a.txt").build(),
                FileRecord.builder().id(2L).originalName("b.txt").build()
        );

        when(fileRecordRepository
                .findByUploadedByAndContextTypeAndContextIdOrderByUploadedAtDesc(1L, "PRIVATE", 5L))
                .thenReturn(files);

        var result = fileController.listMyFiles("PRIVATE", 5L, 1L);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals("a.txt", result.getData().get(0).getOriginalName());
    }

    @Test
    void listMyFiles_withNoFiles_shouldReturnEmptyList() {
        when(fileRecordRepository
                .findByUploadedByAndContextTypeAndContextIdOrderByUploadedAtDesc(2L, "AGENT", 10L))
                .thenReturn(List.of());

        var result = fileController.listMyFiles("AGENT", 10L, 2L);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void buildContentDisposition_shouldEncodeChineseFilename() {
        String fileName = "努力学习的青春最美.md";
        String result = invokeBuildContentDisposition(fileName);

        assertTrue(result.startsWith("inline; filename=\""));
        assertTrue(result.contains("filename*=UTF-8''"));
        assertTrue(result.contains("%E5%8A%AA%E5%8A%9B"), "Should URL-encode Chinese characters");
    }

    @Test
    void buildContentDisposition_shouldHandleAsciiFilename() {
        String result = invokeBuildContentDisposition("report.pdf");
        assertTrue(result.startsWith("inline; filename=\""));
        assertTrue(result.contains("filename*=UTF-8''report.pdf"));
    }

    @Test
    void buildContentDisposition_shouldHandleFilenameWithSpaces() {
        String result = invokeBuildContentDisposition("my report.md");
        assertTrue(result.contains("my report.md") || result.contains("my+report.md"));
    }

    /** 通过反射调用私有静态方法 buildContentDisposition */
    private String invokeBuildContentDisposition(String fileName) {
        try {
            var method = FileController.class.getDeclaredMethod("buildContentDisposition", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
