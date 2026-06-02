package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.KnowledgeAuditLog;
import cafe.snails.ecomagents.model.KnowledgeBase;
import cafe.snails.ecomagents.model.KnowledgeDocument;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {

    @Mock
    private KnowledgeBaseService kbService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;

    private KnowledgeBaseController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeBaseController(kbService, userRepository);
    }

    @Test
    void knowledgeBaseCrudEndpoints_shouldDelegateToService() {
        var kb = KnowledgeBase.builder().id(1L).name("Docs").build();
        when(kbService.listKnowledgeBases()).thenReturn(ApiResponse.success(List.of(kb)));
        when(kbService.getKnowledgeBase(1L)).thenReturn(ApiResponse.success(kb));
        when(kbService.createKnowledgeBase(kb, 7L)).thenReturn(ApiResponse.success(kb));
        when(kbService.updateKnowledgeBase(1L, kb)).thenReturn(ApiResponse.success(kb));
        when(kbService.deleteKnowledgeBase(1L)).thenReturn(ApiResponse.success(null));

        assertEquals(List.of(kb), controller.listKnowledgeBases().getData());
        assertEquals(kb, controller.getKnowledgeBase(1L).getData());
        assertEquals(kb, controller.createKnowledgeBase(kb, 7L).getData());
        assertEquals(kb, controller.updateKnowledgeBase(1L, kb).getData());
        assertEquals(200, controller.deleteKnowledgeBase(1L).getCode());
    }

    @Test
    void documentEndpoints_shouldResolveUsernameAndDelegate() {
        var doc = KnowledgeDocument.builder().id(2L).knowledgeBaseId(1L).fileName("a.txt").build();
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        var files = new MockMultipartFile[]{file};
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().username("alice").build()));
        when(kbService.listDocuments(1L)).thenReturn(ApiResponse.success(List.of(doc)));
        when(kbService.uploadDocument(1L, file, 7L, "alice", request)).thenReturn(ApiResponse.success(doc));
        when(kbService.uploadDocuments(1L, files, 7L, "alice", request)).thenReturn(ApiResponse.success(List.of(doc)));
        when(kbService.deleteDocument(1L, 2L, 7L, "alice", request)).thenReturn(ApiResponse.success(null));

        assertEquals(List.of(doc), controller.listDocuments(1L).getData());
        assertEquals(doc, controller.uploadDocument(1L, file, 7L, request).getData());
        assertEquals(List.of(doc), controller.uploadDocuments(1L, files, 7L, request).getData());
        assertEquals(200, controller.deleteDocument(1L, 2L, 7L, request).getCode());
    }

    @Test
    void documentEndpoints_shouldFallbackToUserIdWhenUsernameMissing() {
        var doc = KnowledgeDocument.builder().id(2L).knowledgeBaseId(1L).fileName("a.txt").build();
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        when(kbService.uploadDocument(1L, file, 7L, "7", request)).thenReturn(ApiResponse.success(doc));

        assertEquals(doc, controller.uploadDocument(1L, file, 7L, request).getData());
    }

    @Test
    void searchAndAuditEndpoints_shouldDelegateToService() {
        var doc = KnowledgeDocument.builder().id(2L).fileName("a.txt").build();
        var log = KnowledgeAuditLog.builder().id(3L).kbId(1L).build();
        when(kbService.search("query")).thenReturn(ApiResponse.success(List.of(doc)));
        when(kbService.getAuditLogs(1L)).thenReturn(List.of(log));
        when(kbService.getAllAuditLogs()).thenReturn(List.of(log));

        assertEquals(List.of(doc), controller.search("query").getData());
        assertEquals(List.of(log), controller.getAuditLogs(1L).getData());
        assertEquals(List.of(log), controller.getAllAuditLogs().getData());
    }
}
