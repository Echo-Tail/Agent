package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.image.ImageSessionDtos.CreateRequest;
import cafe.snails.ecomagents.dto.image.ImageSessionDtos.SaveCanvasRequest;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.exception.ErrorCode;
import cafe.snails.ecomagents.model.CanvasDocument;
import cafe.snails.ecomagents.model.ImageSession;
import cafe.snails.ecomagents.repository.CanvasDocumentRepository;
import cafe.snails.ecomagents.repository.ImageAssetRepository;
import cafe.snails.ecomagents.repository.ImageSessionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageSessionServiceTest {
    @Mock ImageSessionRepository sessionRepository;
    @Mock CanvasDocumentRepository canvasRepository;
    @Mock ImageAssetRepository assetRepository;
    @InjectMocks ImageSessionService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ImageSession session;

    @BeforeEach
    void setUp() {
        session = ImageSession.builder().id(10L).userId(1L).title("商品主图").status("ACTIVE")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void createTrimsTitleAndAssignsOwner() {
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ImageSession value = invocation.getArgument(0);
            value.setId(10L);
            return value;
        });

        var response = service.create(new CreateRequest("  商品主图  "), 1L);

        assertEquals("商品主图", response.title());
        var captor = ArgumentCaptor.forClass(ImageSession.class);
        verify(sessionRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
    }

    @Test
    void deserializesCanvasSnapshotWithTheApplicationJacksonVersion() {
        var request = objectMapper.readValue(
                "{\"revision\":0,\"schemaVersion\":1,\"snapshot\":{\"store\":{\"shape:1\":{\"typeName\":\"shape\"}}}}",
                SaveCanvasRequest.class);

        var store = assertInstanceOf(Map.class, request.snapshot().get("store"));
        var shape = assertInstanceOf(Map.class, store.get("shape:1"));
        assertEquals("shape", shape.get("typeName"));
    }

    @Test
    void getDoesNotExposeAnotherUsersSession() {
        when(sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 2L)).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.get(10L, 2L));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void workspaceReturnsCanvasAndEmptyAssets() {
        var canvas = CanvasDocument.builder().sessionId(10L).revision(3L).schemaVersion(1)
                .snapshot(Map.of()).updatedAt(LocalDateTime.now()).build();
        when(sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(session));
        when(canvasRepository.findById(10L)).thenReturn(Optional.of(canvas));

        var workspace = service.workspace(10L, 1L);

        assertEquals(3L, workspace.canvas().revision());
        assertTrue(workspace.assets().isEmpty());
    }

    @Test
    void firstCanvasSaveRequiresRevisionZero() {
        when(sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(session));
        when(canvasRepository.findById(10L)).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.saveCanvas(10L,
                new SaveCanvasRequest(2L, 1, Map.of()), 1L));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals("CANVAS_REVISION_CONFLICT", error.getMessage());
        verify(canvasRepository, never()).saveAndFlush(any());
    }

    @Test
    void savesANewCanvasDocument() {
        when(sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(session));
        when(canvasRepository.findById(10L)).thenReturn(Optional.empty());
        when(canvasRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.saveCanvas(10L,
                new SaveCanvasRequest(0L, 1, Map.of("page", 1)), 1L);

        assertEquals(10L, response.sessionId());
        verify(sessionRepository).save(session);
        var captor = ArgumentCaptor.forClass(CanvasDocument.class);
        verify(canvasRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getRevision());
    }

    @Test
    void rejectsAStaleCanvasRevision() {
        var canvas = CanvasDocument.builder().sessionId(10L).revision(4L).schemaVersion(1)
                .snapshot(Map.of()).updatedAt(LocalDateTime.now()).build();
        when(sessionRepository.findByIdAndUserIdAndDeletedAtIsNull(10L, 1L)).thenReturn(Optional.of(session));
        when(canvasRepository.findById(10L)).thenReturn(Optional.of(canvas));

        var error = assertThrows(BusinessException.class, () -> service.saveCanvas(10L,
                new SaveCanvasRequest(3L, 1, Map.of()), 1L));

        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertEquals(Map.of("currentRevision", 4L, "suppliedRevision", 3L), error.getData());
    }
}
