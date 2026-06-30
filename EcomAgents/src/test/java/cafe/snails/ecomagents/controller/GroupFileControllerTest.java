package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.model.GroupFile;
import cafe.snails.ecomagents.repository.GroupFileRepository;
import cafe.snails.ecomagents.service.FileStorageService;
import cafe.snails.ecomagents.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 群文件控制器测试，验证群文件上传、列表和下载入口。
 */
class GroupFileControllerTest {

    @Mock
    private GroupFileRepository groupFileRepository;
    @Mock
    private GroupService groupService;
    @Mock
    private FileStorageService fileStorageService;

    @TempDir
    Path tempDir;

    private GroupFileController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupFileController(groupFileRepository, groupService, fileStorageService);
    }

    @Test
    void uploadFile_shouldRejectNonMember() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        when(groupService.isMember(1L, 2L)).thenReturn(false);

        var result = controller.uploadFile(1L, file, 2L);

        assertEquals(403, result.getCode());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void uploadFile_shouldReturnStorageError() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        when(groupService.isMember(1L, 2L)).thenReturn(true);
        when(fileStorageService.uploadFile(file, 2L)).thenReturn(ApiResponse.error(400, "bad file"));

        var result = controller.uploadFile(1L, file, 2L);

        assertEquals(400, result.getCode());
        assertEquals("bad file", result.getMessage());
        verify(groupFileRepository, never()).save(any());
    }

    @Test
    void uploadFile_shouldCreateGroupFileFromFileRecord() {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());
        var record = FileRecord.builder()
                .originalName("a.txt")
                .storedPath("stored/a.txt")
                .fileSize(1L)
                .mimeType("text/plain")
                .build();
        when(groupService.isMember(1L, 2L)).thenReturn(true);
        when(fileStorageService.uploadFile(file, 2L)).thenReturn(ApiResponse.success(record));
        when(groupFileRepository.save(any())).thenAnswer(invocation -> {
            GroupFile gf = invocation.getArgument(0);
            gf.setId(9L);
            return gf;
        });

        var result = controller.uploadFile(1L, file, 2L);

        assertEquals(200, result.getCode());
        assertEquals(9L, result.getData().getId());
        assertEquals(1L, result.getData().getGroupId());
        assertEquals(2L, result.getData().getUploaderId());
        assertEquals("a.txt", result.getData().getOriginalName());
        assertEquals("stored/a.txt", result.getData().getStoragePath());
    }

    @Test
    void listFiles_shouldReturnGroupFiles() {
        var file = GroupFile.builder().id(1L).groupId(3L).originalName("a.txt").build();
        when(groupFileRepository.findByGroupIdOrderByUploadedAtDesc(3L)).thenReturn(List.of(file));

        var result = controller.listFiles(3L);

        assertEquals(List.of(file), result.getData());
    }

    @Test
    void downloadFile_shouldReturnNotFoundWhenRecordMissing() {
        when(groupFileRepository.findById(99L)).thenReturn(Optional.empty());

        var result = controller.downloadFile(1L, 99L);

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void downloadFile_shouldReturnFileResourceAndDispositionHeader() throws Exception {
        Path filePath = tempDir.resolve("report.txt");
        Files.writeString(filePath, "hello");
        var file = GroupFile.builder()
                .id(9L)
                .groupId(1L)
                .originalName("report.txt")
                .mimeType("text/plain")
                .storagePath(filePath.toString())
                .build();
        when(groupFileRepository.findById(9L)).thenReturn(Optional.of(file));

        var result = controller.downloadFile(1L, 9L);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("text/plain", result.getHeaders().getContentType().toString());
        assertTrue(result.getHeaders().getFirst("Content-Disposition").contains("report.txt"));
        assertEquals(5, result.getBody().contentLength());
    }
}
