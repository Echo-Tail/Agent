package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.model.SessionMessage;
import cafe.snails.ecomagents.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMapperTest {

    @Mock
    private SessionRepository sessionRepository;

    @Captor
    private ArgumentCaptor<Session> sessionCaptor;

    @Test
    void resolveHarnessSessionId_shouldRejectSessionOwnedByOtherUser() {
        Session existing = Session.builder()
                .id(10L)
                .agentId(1L)
                .userId(2L)
                .harnessSessionId("sess-1-2-existing")
                .build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(existing));

        SessionMapper mapper = new SessionMapper(sessionRepository);

        assertThrows(IllegalArgumentException.class,
                () -> mapper.resolveHarnessSessionId(10L, 1L, 1L));
        verify(sessionRepository, never()).save(existing);
    }

    @Test
    void resolveHarnessSessionId_shouldRejectSessionForOtherAgent() {
        Session existing = Session.builder()
                .id(10L)
                .agentId(2L)
                .userId(1L)
                .harnessSessionId("sess-2-1-existing")
                .build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(existing));

        SessionMapper mapper = new SessionMapper(sessionRepository);

        assertThrows(IllegalArgumentException.class,
                () -> mapper.resolveHarnessSessionId(10L, 1L, 1L));
        verify(sessionRepository, never()).save(existing);
    }

    @Test
    void resolveHarnessSessionId_shouldReuseOwnedSession() {
        Session existing = Session.builder()
                .id(10L)
                .agentId(1L)
                .userId(1L)
                .harnessSessionId("sess-1-1-existing")
                .build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(existing));

        SessionMapper mapper = new SessionMapper(sessionRepository);

        String result = mapper.resolveHarnessSessionId(10L, 1L, 1L);

        assertEquals("sess-1-1-existing", result);
    }

    @Test
    void resolveHarnessSessionId_shouldBackfillHarnessIdForExistingSession() {
        Session existing = Session.builder()
                .id(10L)
                .agentId(1L)
                .userId(1L)
                .build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(existing));

        SessionMapper mapper = new SessionMapper(sessionRepository);
        String result = mapper.resolveHarnessSessionId(10L, 1L, 1L);

        assertNotNull(result);
        assertEquals(result, existing.getHarnessSessionId());
        verify(sessionRepository).save(existing);
    }

    @Test
    void resolveHarnessSessionId_shouldCreateNewSessionWhenNoDbSessionId() {
        SessionMapper mapper = new SessionMapper(sessionRepository);

        String result = mapper.resolveHarnessSessionId(null, 1L, 1L);

        assertNotNull(result);
        verify(sessionRepository).save(sessionCaptor.capture());
        Session saved = sessionCaptor.getValue();
        assertEquals(1L, saved.getAgentId());
        assertEquals(1L, saved.getUserId());
        assertEquals(result, saved.getHarnessSessionId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void syncSessionMetadata_shouldUpdateTimestampAndGenerateTitleFromUserMessage() {
        Session session = Session.builder()
                .id(10L)
                .title("新对话")
                .harnessSessionId("sess-1-1-test")
                .build();
        when(sessionRepository.findByHarnessSessionId("sess-1-1-test")).thenReturn(Optional.of(session));

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.syncSessionMetadata(1L, "sess-1-1-test", 1L,
                "this is a long user message that should be trimmed at thirty characters", "reply");

        verify(sessionRepository).save(session);
        assertNotNull(session.getUpdatedAt());
        assertEquals(30, session.getTitle().length());
    }

    @Test
    void syncSessionMetadata_shouldDoNothingWhenSessionMissing() {
        when(sessionRepository.findByHarnessSessionId("missing")).thenReturn(Optional.empty());

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.syncSessionMetadata(1L, "missing", 1L, "hello", "reply");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void deleteSession_shouldDeleteExistingSession() {
        Session session = Session.builder().id(10L).build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.deleteSession(10L);

        verify(sessionRepository).delete(session);
    }

    @Test
    void saveMessage_shouldSaveWithFileIdAndFileName() {
        Session session = Session.builder()
                .id(10L)
                .harnessSessionId("sess-1-1-test")
                .messages(new ArrayList<>())
                .build();
        when(sessionRepository.findByHarnessSessionId("sess-1-1-test"))
                .thenReturn(Optional.of(session));

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.saveMessage("sess-1-1-test", "assistant", "file content", 42L, "report.md");

        verify(sessionRepository).save(sessionCaptor.capture());
        Session saved = sessionCaptor.getValue();

        assertNotNull(saved.getMessages());
        assertEquals(1, saved.getMessages().size());

        SessionMessage msg = saved.getMessages().get(0);
        assertEquals("assistant", msg.getRole());
        assertEquals("file content", msg.getContent());
        assertEquals(42L, msg.getFileId());
        assertEquals("report.md", msg.getFileName());
    }

    @Test
    void saveMessage_shouldBeBackwardCompatibleWithoutFileParams() {
        Session session = Session.builder()
                .id(10L)
                .harnessSessionId("sess-1-1-test")
                .messages(new ArrayList<>())
                .build();
        when(sessionRepository.findByHarnessSessionId("sess-1-1-test"))
                .thenReturn(Optional.of(session));

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.saveMessage("sess-1-1-test", "user", "hello");

        verify(sessionRepository).save(sessionCaptor.capture());
        Session saved = sessionCaptor.getValue();

        assertNotNull(saved.getMessages());
        assertEquals(1, saved.getMessages().size());

        SessionMessage msg = saved.getMessages().get(0);
        assertEquals("user", msg.getRole());
        assertEquals("hello", msg.getContent());
        assertNull(msg.getFileId());
        assertNull(msg.getFileName());
    }

    @Test
    void saveMessage_shouldDoNothingWhenSessionNotFound() {
        when(sessionRepository.findByHarnessSessionId("non-existent"))
                .thenReturn(Optional.empty());

        SessionMapper mapper = new SessionMapper(sessionRepository);
        mapper.saveMessage("non-existent", "assistant", "content", 1L, "test.md");

        verify(sessionRepository, never()).save(any());
    }
}
