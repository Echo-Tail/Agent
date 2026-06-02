package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.repository.FileRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FileStorageService} 的单元测试。
 * <p>覆盖文件类型校验、大小校验、空文件名等边界情况。</p>
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    private FileStorageService fileStorageService;

    @Mock
    private FileRecordRepository fileRecordRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(fileRecordRepository);
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempDir.toString());
        lenient().when(fileRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void saveContentAsFile_shouldReturnNullForBlankContent() {
        assertNull(fileStorageService.saveContentAsFile("", "test.txt", 1L));
        assertNull(fileStorageService.saveContentAsFile("   ", "test.md", 1L));
        assertNull(fileStorageService.saveContentAsFile(null, "test.txt", 1L));
    }

    @Test
    void saveContentAsFile_shouldReturnNullForUnsupportedExt() {
        assertNull(fileStorageService.saveContentAsFile("data", "test.exe", 1L));
        assertNull(fileStorageService.saveContentAsFile("data", "archive.zip", 1L));
    }

    @Test
    void saveContentAsFile_shouldSucceedForValidFile() {
        var record = fileStorageService.saveContentAsFile("# Hello", "readme.md", 1L);
        assertNotNull(record);
        assertEquals("readme.md", record.getOriginalName());
        assertEquals(1L, record.getUploadedBy());
        assertEquals("# Hello".length(), record.getFileSize().longValue());
    }

    @Test
    void saveContentAsFile_shouldSupportAllTextExtensions() {
        assertNotNull(fileStorageService.saveContentAsFile("{}", "data.json", 1L));
        assertNotNull(fileStorageService.saveContentAsFile("a,b", "data.csv", 1L));
        assertNotNull(fileStorageService.saveContentAsFile("notes", "notes.txt", 1L));
        assertNotNull(fileStorageService.saveContentAsFile("doc", "doc.pdf", 1L));
        assertNotNull(fileStorageService.saveContentAsFile("# Title", "readme.md", 1L));
    }

    @Test
    void uploadFile_shouldReturnErrorForEmptyFilename() {
        var file = new MockMultipartFile("file", (String) null, "text/plain", new byte[0]);
        ApiResponse<FileRecord> result = fileStorageService.uploadFile(file, 1L);
        assertEquals(400, result.getCode());
        assertEquals("文件名不能为空", result.getMessage());
    }

    @Test
    void uploadFile_shouldReturnErrorForUnsupportedExtension() {
        var file = new MockMultipartFile("file", "test.exe", "application/octet-stream", "data".getBytes());
        ApiResponse<FileRecord> result = fileStorageService.uploadFile(file, 1L);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("不支持的文件类型"));
    }

    @Test
    void uploadFile_shouldReturnErrorForOversizedFile() {
        // 创建一个超过 20MB 的文件
        byte[] largeContent = new byte[21 * 1024 * 1024];
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", largeContent);
        ApiResponse<FileRecord> result = fileStorageService.uploadFile(file, 1L);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("文件大小超出限制"));
    }

    @Test
    void uploadFile_shouldSucceedForValidFile() {
        var file = new MockMultipartFile("file", "test.txt", "text/plain", "hello world".getBytes());
        ApiResponse<FileRecord> result = fileStorageService.uploadFile(file, 1L);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("test.txt", result.getData().getOriginalName());
        assertEquals(1L, result.getData().getUploadedBy());
    }

    @Test
    void uploadFile_shouldAcceptPdfAndDocx() {
        byte[] content = "data".getBytes();
        var pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", content);
        assertEquals(200, fileStorageService.uploadFile(pdf, 1L).getCode());

        var docx = new MockMultipartFile("file", "doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content);
        assertEquals(200, fileStorageService.uploadFile(docx, 1L).getCode());
    }

    @Test
    void uploadFile_shouldAcceptAllSupportedExtensions() {
        byte[] content = "data".getBytes();
        String[][] testCases = {
                {"readme.md", "text/markdown"},
                {"data.json", "application/json"},
                {"data.csv", "text/csv"},
                {"notes.txt", "text/plain"},
                {"doc.pdf", "application/pdf"},
                {"document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"spreadsheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
        };
        for (String[] tc : testCases) {
            var file = new MockMultipartFile("file", tc[0], tc[1], content);
            ApiResponse<FileRecord> result = fileStorageService.uploadFile(file, 1L);
            assertEquals(200, result.getCode(), "Should accept " + tc[0]);
        }
    }
}
