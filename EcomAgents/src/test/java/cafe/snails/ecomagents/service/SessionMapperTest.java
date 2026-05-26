package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.Session;
import cafe.snails.ecomagents.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionMapperTest {

    @Mock
    private SessionRepository sessionRepository;

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
}
