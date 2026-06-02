package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.GroupMessage;
import cafe.snails.ecomagents.model.SenderType;
import cafe.snails.ecomagents.repository.GroupAgentRepository;
import cafe.snails.ecomagents.repository.GroupMemberRepository;
import cafe.snails.ecomagents.repository.GroupMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupMessageServiceTest {

    @Mock
    private GroupMessageRepository groupMessageRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupAgentRepository groupAgentRepository;
    @Mock
    private GroupSseService groupSseService;
    @Mock
    private GroupService groupService;
    @Mock
    private HarnessChatService harnessChatService;

    private GroupMessageService service;

    @BeforeEach
    void setUp() {
        service = new GroupMessageService(groupMessageRepository, groupMemberRepository,
                groupAgentRepository, groupSseService, groupService, harnessChatService, new ObjectMapper());
    }

    @Test
    void sendMessage_shouldRejectNonMember() {
        when(groupService.isMember(1L, 2L)).thenReturn(false);

        var result = service.sendMessage(1L, 2L, "hello");

        assertEquals(403, result.getCode());
        verify(groupMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectBlankContent() {
        when(groupService.isMember(1L, 2L)).thenReturn(true);

        var result = service.sendMessage(1L, 2L, "   ");

        assertEquals(400, result.getCode());
        verify(groupMessageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldPersistTrimmedUserMessageAndBroadcastEvents() {
        when(groupService.isMember(1L, 2L)).thenReturn(true);
        when(groupMessageRepository.save(any())).thenAnswer(invocation -> {
            GroupMessage msg = invocation.getArgument(0);
            msg.setId(99L);
            msg.setCreatedAt(LocalDateTime.of(2026, 6, 2, 12, 0));
            return msg;
        });

        var result = service.sendMessage(1L, 2L, "  hello  ");

        assertEquals(200, result.getCode());
        assertEquals("hello", result.getData().getContent());
        assertEquals(SenderType.USER, result.getData().getSenderType());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(groupSseService).broadcast(eq(1L), eq("message"), payloadCaptor.capture());
        assertEquals(99L, payloadCaptor.getValue().get("id"));
        assertEquals("hello", payloadCaptor.getValue().get("content"));
        verify(groupSseService).broadcast(eq(1L), eq("unread_group"), payloadCaptor.capture());
        assertEquals(1L, payloadCaptor.getValue().get("groupId"));
        assertEquals(2L, payloadCaptor.getValue().get("senderId"));
    }

    @Test
    void sendMessage_shouldSkipMentionedAgentThatIsNotInGroup() {
        when(groupService.isMember(1L, 2L)).thenReturn(true);
        when(groupMessageRepository.save(any())).thenAnswer(invocation -> {
            GroupMessage msg = invocation.getArgument(0);
            msg.setId(100L);
            msg.setCreatedAt(LocalDateTime.of(2026, 6, 2, 12, 0));
            return msg;
        });
        when(groupAgentRepository.existsByGroupIdAndAgentId(1L, 7L)).thenReturn(false);

        var result = service.sendMessage(1L, 2L, "@[Helper](agent:7) please help");

        assertEquals(200, result.getCode());
        verify(groupAgentRepository).existsByGroupIdAndAgentId(1L, 7L);
        verifyNoInteractions(harnessChatService);
    }

    @Test
    void listMessages_shouldUseDescendingPagedRepositoryQuery() {
        var message = GroupMessage.builder().id(1L).groupId(10L).content("old").build();
        when(groupMessageRepository.findByGroupIdOrderByCreatedAtDesc(eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(message));

        var result = service.listMessages(10L, 2, 25);

        assertEquals(200, result.getCode());
        assertEquals(List.of(message), result.getData());
        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(groupMessageRepository).findByGroupIdOrderByCreatedAtDesc(eq(10L), pageCaptor.capture());
        assertEquals(2, pageCaptor.getValue().getPageNumber());
        assertEquals(25, pageCaptor.getValue().getPageSize());
    }
}
