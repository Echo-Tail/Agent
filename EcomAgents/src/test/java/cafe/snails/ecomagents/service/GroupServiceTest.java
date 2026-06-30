package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.*;
import cafe.snails.ecomagents.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link GroupService} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private ChatGroupRepository chatGroupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private GroupAgentRepository groupAgentRepository;
    @Mock
    private AgentService agentService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AgentRepository agentRepository;

    private GroupService groupService;

    private final Long userId1 = 1L;
    private final Long userId2 = 2L;
    private final Long userId3 = 3L;
    private final Long agentId = 100L;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(chatGroupRepository, groupMemberRepository,
                groupAgentRepository, agentService, agentRepository, userRepository);
    }

    // ===== 创建群 =====

    @Test
    void createGroup_shouldCreateGroupAndAddCreator() {
        ChatGroup savedGroup = ChatGroup.builder()
                .id(10L).name("测试群").createdBy(userId1)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(chatGroupRepository.save(any())).thenReturn(savedGroup);
        when(groupMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<ChatGroup> resp = groupService.createGroup("测试群", null, userId1);

        assertEquals(200, resp.getCode());
        assertEquals("测试群", resp.getData().getName());

        // 验证创建者被添加为 CREATOR 成员
        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        GroupMember creator = captor.getValue();
        assertEquals(10L, creator.getGroupId());
        assertEquals(userId1, creator.getUserId());
        assertEquals(GroupRole.CREATOR, creator.getRole());
    }

    @Test
    void createGroup_shouldSetNullNameAsEmpty() {
        ChatGroup savedGroup = ChatGroup.builder().id(1L).name("").createdBy(userId1)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(chatGroupRepository.save(any())).thenReturn(savedGroup);
        when(groupMemberRepository.save(any())).thenReturn(mock(GroupMember.class));

        ApiResponse<ChatGroup> resp = groupService.createGroup("", null, userId1);

        assertEquals(200, resp.getCode());
    }

    // ===== 群列表 =====

    @Test
    void listMyGroups_shouldReturnUserGroups() {
        when(groupMemberRepository.findByUserId(userId1))
                .thenReturn(List.of(
                        GroupMember.builder().id(1L).groupId(10L).userId(userId1).build(),
                        GroupMember.builder().id(2L).groupId(20L).userId(userId1).build()
                ));
        when(chatGroupRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(
                        ChatGroup.builder().id(10L).name("群A").createdBy(userId1).build(),
                        ChatGroup.builder().id(20L).name("群B").createdBy(userId2).build()
                ));

        ApiResponse<List<ChatGroup>> resp = groupService.listMyGroups(userId1);

        assertEquals(200, resp.getCode());
        assertEquals(2, resp.getData().size());
    }

    // ===== 修改群信息 =====

    @Test
    void updateGroup_shouldAllowCreatorOnly() {
        ChatGroup group = ChatGroup.builder().id(5L).name("旧名称").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(chatGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 创建者可以修改
        ApiResponse<ChatGroup> resp = groupService.updateGroup(5L, "新名称", null, userId1);
        assertEquals(200, resp.getCode());
        assertEquals("新名称", resp.getData().getName());

        // 非创建者不能修改
        ApiResponse<ChatGroup> resp2 = groupService.updateGroup(5L, "新名称", null, userId2);
        assertEquals(403, resp2.getCode());
    }

    // ===== 解散群 =====

    @Test
    void disbandGroup_shouldCleanupAndDelete() {
        ChatGroup group = ChatGroup.builder().id(5L).name("测试群").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));

        ApiResponse<Void> resp = groupService.disbandGroup(5L, userId1);

        assertEquals(200, resp.getCode());
        verify(groupMemberRepository).deleteByGroupId(5L);
        verify(groupAgentRepository).deleteByGroupId(5L);
        verify(chatGroupRepository).delete(group);
    }

    @Test
    void disbandGroup_shouldRejectNonCreator() {
        ChatGroup group = ChatGroup.builder().id(5L).name("测试群").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));

        ApiResponse<Void> resp = groupService.disbandGroup(5L, userId2);

        assertEquals(403, resp.getCode());
        verify(groupMemberRepository, never()).deleteByGroupId(any());
    }

    // ===== 邀请成员 =====

    @Test
    void inviteMember_shouldAllowExistingMember() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId2)).thenReturn(false);

        ApiResponse<Void> resp = groupService.inviteMember(5L, userId2, userId1);

        assertEquals(200, resp.getCode());
        verify(groupMemberRepository).save(any());
    }

    @Test
    void inviteMember_shouldRejectNonMember() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(false);

        ApiResponse<Void> resp = groupService.inviteMember(5L, userId2, userId1);

        assertEquals(403, resp.getCode());
    }

    @Test
    void inviteMember_shouldRejectAlreadyInGroup() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId2)).thenReturn(true);

        ApiResponse<Void> resp = groupService.inviteMember(5L, userId2, userId1);

        assertEquals(400, resp.getCode());
        verify(groupMemberRepository, never()).save(any());
    }

    // ===== 踢人 =====

    @Test
    void kickMember_shouldAllowCreatorOnly() {
        ChatGroup group = ChatGroup.builder().id(5L).name("测试群").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));

        ApiResponse<Void> resp = groupService.kickMember(5L, userId2, userId1);
        assertEquals(200, resp.getCode());
        verify(groupMemberRepository).deleteByGroupIdAndUserId(5L, userId2);
    }

    @Test
    void kickMember_shouldRejectNonCreator() {
        ChatGroup group = ChatGroup.builder().id(5L).name("测试群").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));

        ApiResponse<Void> resp = groupService.kickMember(5L, userId2, userId3);

        assertEquals(403, resp.getCode());
    }

    @Test
    void kickMember_shouldNotKickCreator() {
        ChatGroup group = ChatGroup.builder().id(5L).name("测试群").createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));

        ApiResponse<Void> resp = groupService.kickMember(5L, userId1, userId1);

        assertEquals(400, resp.getCode());
    }

    // ===== 拉 Agent =====

    @Test
    void addAgent_shouldAllowMemberWithOwnAgent() {
        Agent mockAgent = new Agent();
        mockAgent.setId(agentId);
        mockAgent.setCreatedBy(userId1);

        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupAgentRepository.existsByGroupIdAndAgentId(5L, agentId)).thenReturn(false);
        when(agentService.getAgent(agentId)).thenReturn(ApiResponse.success(mockAgent));

        ApiResponse<Void> resp = groupService.addAgent(5L, agentId, userId1);

        assertEquals(200, resp.getCode());
        verify(groupAgentRepository).save(any());
    }

    @Test
    void addAgent_shouldRejectIfNotMember() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(false);

        ApiResponse<Void> resp = groupService.addAgent(5L, agentId, userId1);

        assertEquals(403, resp.getCode());
    }

    @Test
    void addAgent_shouldRejectIfAgentAlreadyInGroup() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupAgentRepository.existsByGroupIdAndAgentId(5L, agentId)).thenReturn(true);

        ApiResponse<Void> resp = groupService.addAgent(5L, agentId, userId1);

        assertEquals(400, resp.getCode());
    }

    @Test
    void addAgent_shouldRejectOthersAgent() {
        Agent mockAgent = new Agent();
        mockAgent.setId(agentId);
        mockAgent.setCreatedBy(userId2); // 不是 userId1 的 Agent

        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupAgentRepository.existsByGroupIdAndAgentId(5L, agentId)).thenReturn(false);
        when(agentService.getAgent(agentId)).thenReturn(ApiResponse.success(mockAgent));

        ApiResponse<Void> resp = groupService.addAgent(5L, agentId, userId1);

        assertEquals(403, resp.getCode());
    }

    // ===== 辅助方法 =====

    @Test
    void isMember_shouldReturnCorrectly() {
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId1)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(5L, userId2)).thenReturn(false);

        assertTrue(groupService.isMember(5L, userId1));
        assertFalse(groupService.isMember(5L, userId2));
    }

    @Test
    void isCreator_shouldReturnCorrectly() {
        ChatGroup group = ChatGroup.builder().id(5L).createdBy(userId1).build();
        when(chatGroupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(chatGroupRepository.findById(999L)).thenReturn(Optional.empty());

        assertTrue(groupService.isCreator(5L, userId1));
        assertFalse(groupService.isCreator(5L, userId2));
        assertFalse(groupService.isCreator(999L, userId1));
    }
}
