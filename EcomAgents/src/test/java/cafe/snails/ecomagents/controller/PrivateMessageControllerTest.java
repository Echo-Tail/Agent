package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.model.ChatPrivateMessage;
import cafe.snails.ecomagents.model.User;
import cafe.snails.ecomagents.repository.ChatPrivateMessageRepository;
import cafe.snails.ecomagents.repository.UserRepository;
import cafe.snails.ecomagents.service.PrivateSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateMessageControllerTest {

    @Mock
    private ChatPrivateMessageRepository privateMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrivateSseService privateSseService;

    private PrivateMessageController controller;

    @BeforeEach
    void setUp() {
        controller = new PrivateMessageController(privateMessageRepository, userRepository, privateSseService);
    }

    @Test
    void sendMessage_shouldRejectMissingReceiverId() {
        var result = controller.sendMessage(Map.of("content", "hello"), 1L);

        assertEquals(400, result.getCode());
        verify(privateMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectBlankContent() {
        var result = controller.sendMessage(Map.of("receiverId", 2L, "content", "   "), 1L);

        assertEquals(400, result.getCode());
        verify(privateMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldPersistTrimmedMessageAndPushSseEvents() {
        when(privateMessageRepository.save(any())).thenAnswer(invocation -> {
            ChatPrivateMessage msg = invocation.getArgument(0);
            msg.setId(10L);
            msg.setCreatedAt(LocalDateTime.of(2026, 6, 2, 12, 0));
            return msg;
        });
        when(privateMessageRepository.countUnreadByReceiverId(2L)).thenReturn(List.<Object[]>of(new Object[]{1L, 4L}));

        var result = controller.sendMessage(Map.of("receiverId", "2", "content", "  hello  "), 1L);

        assertEquals(200, result.getCode());
        assertEquals("hello", result.getData().getContent());
        assertEquals(1L, result.getData().getSenderId());
        assertEquals(2L, result.getData().getReceiverId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(privateSseService).sendToUser(eq(2L), eq("message"), payloadCaptor.capture());
        assertEquals(10L, payloadCaptor.getValue().get("id"));
        verify(privateSseService).sendToUser(eq(2L), eq("unread_private"), payloadCaptor.capture());
        assertEquals(1L, payloadCaptor.getValue().get("userId"));
        assertEquals(5L, payloadCaptor.getValue().get("count"));
    }

    @Test
    void getConversation_shouldUsePagedConversationQuery() {
        var message = ChatPrivateMessage.builder().id(1L).content("hello").build();
        when(privateMessageRepository.findConversation(eq(1L), eq(2L), any(PageRequest.class)))
                .thenReturn(List.of(message));

        var result = controller.getConversation(2L, 3, 20, 1L);

        assertEquals(200, result.getCode());
        assertEquals(List.of(message), result.getData());
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(privateMessageRepository).findConversation(eq(1L), eq(2L), pageCaptor.capture());
        assertEquals(3, pageCaptor.getValue().getPageNumber());
        assertEquals(20, pageCaptor.getValue().getPageSize());
    }

    @Test
    void getUnreadSummary_shouldMapRepositoryRows() {
        when(privateMessageRepository.countUnreadByReceiverId(2L))
                .thenReturn(List.of(new Object[]{1L, 3L}, new Object[]{4L, 1L}));

        var result = controller.getUnreadSummary(2L);

        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
        assertEquals(1L, result.getData().get(0).get("userId"));
        assertEquals(3L, result.getData().get(0).get("count"));
    }

    @Test
    void markAsRead_shouldDelegateRepositoryUpdate() {
        var result = controller.markAsRead(3L, 2L);

        assertEquals(200, result.getCode());
        verify(privateMessageRepository).markConversationAsRead(2L, 3L);
    }

    @Test
    void subscribe_shouldCreateEmitterForCurrentUser() {
        var emitter = new SseEmitter();
        when(privateSseService.createEmitter(2L)).thenReturn(emitter);

        assertSame(emitter, controller.subscribe(2L));
    }

    @Test
    void getContacts_shouldReturnOnlyExistingUsers() {
        when(privateMessageRepository.findContactUserIds(2L)).thenReturn(List.of(1L, 3L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().username("alice").build()));
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        var result = controller.getContacts(2L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getData().get(0).get("userId"));
        assertEquals("alice", result.getData().get(0).get("username"));
    }
}
