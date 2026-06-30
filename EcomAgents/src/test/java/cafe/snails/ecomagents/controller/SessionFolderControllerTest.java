package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.model.SessionFolder;
import cafe.snails.ecomagents.repository.SessionFolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 会话文件夹控制器测试，验证文件夹增删改查和排序。
 */
class SessionFolderControllerTest {

    @Mock
    private SessionFolderRepository folderRepository;

    private SessionFolderController controller;

    @BeforeEach
    void setUp() {
        controller = new SessionFolderController(folderRepository);
    }

    @Test
    void listFolders_shouldReturnCurrentUserFolders() {
        var folder = SessionFolder.builder().id(1L).name("Work").userId(9L).build();
        when(folderRepository.findByUserIdOrderByOrderNum(9L)).thenReturn(List.of(folder));

        var result = controller.listFolders(9L);

        assertEquals(200, result.getCode());
        assertEquals(List.of(folder), result.getData());
    }

    @Test
    void createFolder_shouldPersistNameAndUserId() {
        when(folderRepository.save(any())).thenAnswer(invocation -> {
            SessionFolder folder = invocation.getArgument(0);
            folder.setId(10L);
            return folder;
        });

        var result = controller.createFolder(Map.of("name", "Pinned"), 3L);

        assertEquals(200, result.getCode());
        assertEquals(10L, result.getData().getId());
        assertEquals("Pinned", result.getData().getName());
        assertEquals(3L, result.getData().getUserId());
    }

    @Test
    void updateFolder_shouldRenameOwnedFolder() {
        var folder = SessionFolder.builder().id(1L).name("Old").userId(3L).build();
        when(folderRepository.findByIdAndUserId(1L, 3L)).thenReturn(Optional.of(folder));
        when(folderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = controller.updateFolder(1L, Map.of("name", "New"), 3L);

        assertEquals(200, result.getCode());
        assertEquals("New", result.getData().getName());
    }

    @Test
    void updateFolder_shouldReturn404ForMissingFolder() {
        when(folderRepository.findByIdAndUserId(99L, 3L)).thenReturn(Optional.empty());

        var result = controller.updateFolder(99L, Map.of("name", "New"), 3L);

        assertEquals(404, result.getCode());
        verify(folderRepository, never()).save(any());
    }

    @Test
    void deleteFolder_shouldDeleteOwnedFolder() {
        var folder = SessionFolder.builder().id(1L).name("Old").userId(3L).build();
        when(folderRepository.findByIdAndUserId(1L, 3L)).thenReturn(Optional.of(folder));

        var result = controller.deleteFolder(1L, 3L);

        assertEquals(200, result.getCode());
        verify(folderRepository).delete(folder);
    }

    @Test
    void deleteFolder_shouldReturn404ForMissingFolder() {
        when(folderRepository.findByIdAndUserId(99L, 3L)).thenReturn(Optional.empty());

        var result = controller.deleteFolder(99L, 3L);

        assertEquals(404, result.getCode());
        verify(folderRepository, never()).delete(any());
    }
}
