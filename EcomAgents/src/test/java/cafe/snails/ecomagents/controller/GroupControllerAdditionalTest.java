package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.ChatGroup;
import cafe.snails.ecomagents.repository.GroupMessageRepository;
import cafe.snails.ecomagents.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
/**
 * 群控制器补充测试，覆盖群列表、创建、更新和删除等边界行为。
 */
class GroupControllerAdditionalTest {

    @Mock
    private GroupService groupService;
    @Mock
    private GroupMessageRepository groupMessageRepository;

    private GroupController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupController(groupService, groupMessageRepository);
    }

    @Test
    void createGroup_shouldRejectBlankName() {
        var result = controller.createGroup(Map.of("name", "   "), 1L);

        assertEquals(400, result.getCode());
        verifyNoInteractions(groupService);
    }

    @Test
    void createGroup_shouldTrimNameAndDelegate() {
        var group = ChatGroup.builder().id(2L).name("Team").build();
        when(groupService.createGroup("Team", "avatar.png", 1L)).thenReturn(ApiResponse.success(group));

        var result = controller.createGroup(Map.of("name", " Team ", "avatar", "avatar.png"), 1L);

        assertEquals(200, result.getCode());
        assertEquals(group, result.getData());
    }

    @Test
    void delegateEndpoints_shouldCallGroupService() {
        var group = ChatGroup.builder().id(2L).name("Team").build();
        var agent = Agent.builder().id(7L).name("Helper").build();
        when(groupService.listMyGroups(1L)).thenReturn(ApiResponse.success(List.of(group)));
        when(groupService.getGroup(2L)).thenReturn(ApiResponse.success(group));
        when(groupService.updateGroup(2L, "New", "a.png", 1L)).thenReturn(ApiResponse.success(group));
        when(groupService.disbandGroup(2L, 1L)).thenReturn(ApiResponse.success(null));
        when(groupService.uploadAvatar(eq(2L), any(), eq(1L))).thenReturn(ApiResponse.success("url"));
        when(groupService.getInvitableAgents(2L, 1L)).thenReturn(ApiResponse.success(List.of(agent)));

        assertEquals(List.of(group), controller.listMyGroups(1L).getData());
        assertEquals(group, controller.getGroup(2L).getData());
        assertEquals(group, controller.updateGroup(2L, Map.of("name", "New", "avatar", "a.png"), 1L).getData());
        assertEquals(200, controller.disbandGroup(2L, 1L).getCode());
        assertEquals("url", controller.uploadAvatar(2L, new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}), 1L).getData());
        assertEquals(List.of(agent), controller.getInvitableAgents(2L, 1L).getData());
    }

    @Test
    void getGroupUnreadSummary_shouldFilterZeroCounts() {
        var groupA = ChatGroup.builder().id(1L).name("A").build();
        var groupB = ChatGroup.builder().id(2L).name("B").build();
        when(groupService.listMyGroups(5L)).thenReturn(ApiResponse.success(List.of(groupA, groupB)));
        when(groupMessageRepository.countUnreadByGroupId(1L, 5L)).thenReturn(0L);
        when(groupMessageRepository.countUnreadByGroupId(2L, 5L)).thenReturn(3L);

        var result = controller.getGroupUnreadSummary(5L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals(2L, result.getData().get(0).get("groupId"));
        assertEquals(3L, result.getData().get(0).get("count"));
    }

    @Test
    void markGroupAsRead_shouldDelegateRepositoryUpdate() {
        var result = controller.markGroupAsRead(2L, 5L);

        assertEquals(200, result.getCode());
        verify(groupMessageRepository).markGroupAsRead(2L, 5L);
    }
}
