package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SessionSummary;
import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionService service;

    private Session nonEmptySession;
    private Session emptySession;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository);

        nonEmptySession = Session.builder()
                .id(1L).agentId(1L).title("有消息的会话")
                .messages(new ArrayList<>(List.of(
                        SessionMessage.builder().role("user").content("你好").timestamp(LocalDateTime.now()).build(),
                        SessionMessage.builder().role("assistant").content("你好！有什么可以帮助你的？").timestamp(LocalDateTime.now()).build()
                )))
                .tags(new ArrayList<>())
                .createdAt(LocalDateTime.now().minusHours(2))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        emptySession = Session.builder()
                .id(2L).agentId(1L).title("新对话")
                .messages(new ArrayList<>())
                .tags(new ArrayList<>())
                .createdAt(LocalDateTime.now().minusHours(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    @Test
    void listSessions_shouldReturnOnlyNonEmptySessions() {
        when(sessionRepository.findAllNonEmpty()).thenReturn(List.of(nonEmptySession));

        ApiResponse<List<SessionSummary>> result = service.listSessions(null, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("有消息的会话", result.getData().get(0).getTitle());
        assertEquals(2, result.getData().get(0).getMessageCount());
    }

    @Test
    void listSessions_withFolderId_shouldUseNonEmptyQuery() {
        when(sessionRepository.findNonEmptyByFolderId(1L)).thenReturn(List.of(nonEmptySession));

        ApiResponse<List<SessionSummary>> result = service.listSessions(1L, null);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        verify(sessionRepository).findNonEmptyByFolderId(1L);
    }

    @Test
    void listSessions_withAgentId_shouldUseNonEmptyQuery() {
        when(sessionRepository.findNonEmptyByAgentId(1L)).thenReturn(List.of(nonEmptySession));

        ApiResponse<List<SessionSummary>> result = service.listSessions(null, 1L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        verify(sessionRepository).findNonEmptyByAgentId(1L);
    }

    @Test
    void listSessions_whenAllEmpty_shouldReturnEmptyList() {
        when(sessionRepository.findAllNonEmpty()).thenReturn(List.of());

        ApiResponse<List<SessionSummary>> result = service.listSessions(null, null);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void cleanupEmptySessions_shouldDeleteEmptyAndReturnCount() {
        when(sessionRepository.deleteAllEmpty()).thenReturn(5);

        int deleted = service.cleanupEmptySessions();

        assertEquals(5, deleted);
        verify(sessionRepository).deleteAllEmpty();
    }

    @Test
    void cleanupEmptySessions_whenNoneEmpty_shouldReturnZero() {
        when(sessionRepository.deleteAllEmpty()).thenReturn(0);

        int deleted = service.cleanupEmptySessions();

        assertEquals(0, deleted);
    }
}
