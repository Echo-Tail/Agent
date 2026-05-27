package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.repository.FileRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private FileRecordRepository fileRecordRepository;

    @InjectMocks
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    @Test
    void uploadFile_shouldSucceed() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello, World!".getBytes());

        var savedRecord = FileRecord.builder()
                .id(1L)
                .originalName("test.txt")
                .fileSize(13L)
                .mimeType("text/plain")
                .build();

        when(fileRecordRepository.save(any())).thenReturn(savedRecord);

        var result = fileStorageService.uploadFile(file, 1L);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("test.txt", result.getData().getOriginalName());
    }

    @Test
    void uploadFile_shouldRejectUnsupportedExtension() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "bad".getBytes());

        var result = fileStorageService.uploadFile(file, 1L);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void uploadFile_shouldRejectEmptyFilename() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var file = new MockMultipartFile(
                "file", "", "text/plain", "content".getBytes());

        var result = fileStorageService.uploadFile(file, 1L);

        assertEquals(400, result.getCode());
        assertEquals("文件名不能为空", result.getMessage());
    }

    @Test
    void saveContentAsFile_shouldSaveMdContentAndReturnRecord() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        String content = "# Hello\n\nWorld";
        var savedRecord = FileRecord.builder()
                .id(1L)
                .originalName("test.md")
                .fileSize(14L)
                .mimeType("text/markdown")
                .build();

        when(fileRecordRepository.save(any())).thenReturn(savedRecord);

        var result = fileStorageService.saveContentAsFile(content, "test.md", 1L);

        assertNotNull(result);
        assertEquals("test.md", result.getOriginalName());
        verify(fileRecordRepository).save(any());
    }

    @Test
    void saveContentAsFile_shouldRejectNullContent() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var result = fileStorageService.saveContentAsFile(null, "test.md", 1L);

        assertNull(result);
    }

    @Test
    void saveContentAsFile_shouldRejectBlankContent() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var result = fileStorageService.saveContentAsFile("   ", "test.md", 1L);

        assertNull(result);
    }

    @Test
    void saveContentAsFile_shouldRejectUnsupportedExtension() {
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());

        var result = fileStorageService.saveContentAsFile("content", "test.exe", 1L);

        assertNull(result);
    }
}
