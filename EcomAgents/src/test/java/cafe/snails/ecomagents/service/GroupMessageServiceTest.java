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
/**
 * 群消息服务测试，验证群消息发送、历史查询和 Agent 提及触发逻辑。
 */
class GroupMessageServiceTest {

    @Mock
    private GroupMessageRepository groupMessageRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupAgentRepository groupAgentRepository;
    @Mock
    private SseService sseService;
    @Mock
    private GroupService groupService;
    @Mock
    private HarnessChatService harnessChatService;

    private GroupMessageService service;

    @BeforeEach
    void setUp() {
        service = new GroupMessageService(groupMessageRepository, groupMemberRepository,
                groupAgentRepository, sseService, groupService, harnessChatService, new ObjectMapper());
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
        verify(sseService).broadcast(eq(1L), eq("message"), payloadCaptor.capture());
        assertEquals(99L, payloadCaptor.getValue().get("id"));
        assertEquals("hello", payloadCaptor.getValue().get("content"));
        verify(sseService).broadcast(eq(1L), eq("unread_group"), payloadCaptor.capture());
        assertEquals(1L, payloadCaptor.getValue().get("groupId"));
        assertEquals(2L, payloadCaptor.getValue().get("senderId"));
    }

    @Test
    void sendMessage_shouldPersistMessageEvenWhenBroadcastFails() {
        // 验证：SSE 广播失败不应影响消息保存（原 @Transactional 已移除）
        when(groupService.isMember(1L, 2L)).thenReturn(true);
        when(groupMessageRepository.save(any())).thenAnswer(invocation -> {
            GroupMessage msg = invocation.getArgument(0);
            msg.setId(88L);
            msg.setCreatedAt(LocalDateTime.of(2026, 6, 2, 12, 0));
            return msg;
        });
        // broadcast 抛出异常（广播在 save 之后执行，save 已提交）
        doThrow(new RuntimeException("SSE 连接已断开"))
                .when(sseService).broadcast(eq(1L), eq("message"), any());

        // sendMessage 会由于 broadcast 异常而向外抛，但验证 save 已发生
        assertThrows(RuntimeException.class,
                () -> service.sendMessage(1L, 2L, "broadcast will fail"));
        // save 已被调用（消息已持久化到 DB），广播失败不影响
        verify(groupMessageRepository).save(any());
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
