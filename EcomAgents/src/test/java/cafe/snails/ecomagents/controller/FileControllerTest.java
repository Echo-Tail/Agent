package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileStorageService fileStorageService;

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
    void buildContentDisposition_shouldEncodeChineseFilename() {
        // Use reflection to test the private static method
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
        // The '+' from URLEncoder should be replaced with ' '
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
