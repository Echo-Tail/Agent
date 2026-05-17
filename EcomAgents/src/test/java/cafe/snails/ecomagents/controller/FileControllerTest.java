package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.FileRecord;
import cafe.snails.ecomagents.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
