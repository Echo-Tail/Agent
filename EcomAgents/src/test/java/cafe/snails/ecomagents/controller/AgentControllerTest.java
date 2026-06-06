package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ToolAvailability;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.service.AgentService;
import cafe.snails.ecomagents.service.AgentToolAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Agent 控制器测试，覆盖全部 8 个端点。
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentService agentService;

    @Mock
    private AgentToolAvailabilityService toolAvailabilityService;

    @InjectMocks
    private AgentController controller;

    private Agent sampleAgent;

    @BeforeEach
    void setUp() {
        sampleAgent = Agent.builder().id(1L).name("测试助手").modelId(10L).build();
    }

    @Test
    void listAgents_withScopeMy_shouldDelegateToService() {
        when(agentService.listAgents(1L, "my")).thenReturn(
                ApiResponse.success(List.of(sampleAgent)));

        var result = controller.listAgents("my", 1L);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("测试助手", result.getData().get(0).getName());
        verify(agentService).listAgents(1L, "my");
    }

    @Test
    void listAgents_withScopePlaza_shouldDelegateToService() {
        when(agentService.listAgents(2L, "plaza")).thenReturn(
                ApiResponse.success(List.of()));

        var result = controller.listAgents("plaza", 2L);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
        verify(agentService).listAgents(2L, "plaza");
    }

    @Test
    void listAgents_withoutScope_shouldCallNoArgList() {
        when(agentService.listAgents()).thenReturn(
                ApiResponse.success(List.of(sampleAgent)));

        var result = controller.listAgents(null, 1L);

        assertEquals(200, result.getCode());
        verify(agentService).listAgents();
        verify(agentService, never()).listAgents(anyLong(), anyString());
    }

    @Test
    void getSystemAgent_shouldReturnSystemAgent() {
        var systemAgent = Agent.builder().id(0L).name("System").build();
        when(agentService.getOrInitSystemAgent()).thenReturn(
                ApiResponse.success(systemAgent));

        var result = controller.getSystemAgent();

        assertEquals(200, result.getCode());
        assertEquals("System", result.getData().getName());
        verify(agentService).getOrInitSystemAgent();
    }

    @Test
    void getAgent_shouldReturnAgentById() {
        when(agentService.getAgent(1L)).thenReturn(
                ApiResponse.success(sampleAgent));

        var result = controller.getAgent(1L);

        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getId());
        verify(agentService).getAgent(1L);
    }

    @Test
    void getAgent_shouldReturn404ForMissingAgent() {
        when(agentService.getAgent(999L)).thenReturn(
                ApiResponse.error(404, "Agent 不存在"));

        var result = controller.getAgent(999L);

        assertEquals(404, result.getCode());
        assertEquals("Agent 不存在", result.getMessage());
    }

    @Test
    void getWebSearchAvailability_shouldReturnToolAvailability() {
        var availability = new ToolAvailability("web_search", 0L, true, false, false, false, "");
        when(toolAvailabilityService.getWebSearchAvailability(1L)).thenReturn(availability);

        var result = controller.getWebSearchAvailability(1L);

        assertEquals(200, result.getCode());
        assertEquals("web_search", result.getData().getToolId());
        verify(toolAvailabilityService).getWebSearchAvailability(1L);
    }

    @Test
    void createAgent_shouldReturnCreatedAgent() {
        when(agentService.createAgent(sampleAgent, 1L)).thenReturn(
                ApiResponse.success(sampleAgent));

        var result = controller.createAgent(sampleAgent, 1L);

        assertEquals(200, result.getCode());
        assertEquals("测试助手", result.getData().getName());
        verify(agentService).createAgent(sampleAgent, 1L);
    }

    @Test
    void updateAgent_shouldReturnUpdatedAgent() {
        var update = Agent.builder().name("新名称").build();
        when(agentService.updateAgent(1L, update, 2L)).thenReturn(
                ApiResponse.success(Agent.builder().id(1L).name("新名称").build()));

        var result = controller.updateAgent(1L, update, 2L);

        assertEquals(200, result.getCode());
        assertEquals("新名称", result.getData().getName());
        verify(agentService).updateAgent(1L, update, 2L);
    }

    @Test
    void updateAgent_shouldReturn403ForUnauthorized() {
        var update = Agent.builder().name("新名称").build();
        when(agentService.updateAgent(1L, update, 3L)).thenReturn(
                ApiResponse.error(403, "无权限修改此 Agent"));

        var result = controller.updateAgent(1L, update, 3L);

        assertEquals(403, result.getCode());
        verify(agentService).updateAgent(1L, update, 3L);
    }

    @Test
    void deleteAgent_shouldReturnDeletedAgent() {
        when(agentService.deleteAgent(1L, 1L)).thenReturn(
                ApiResponse.success(sampleAgent));

        var result = controller.deleteAgent(1L, 1L);

        assertEquals(200, result.getCode());
        verify(agentService).deleteAgent(1L, 1L);
    }

    @Test
    void deleteAgent_shouldReturn403ForUnauthorized() {
        when(agentService.deleteAgent(1L, 2L)).thenReturn(
                ApiResponse.error(403, "无权限删除此 Agent"));

        var result = controller.deleteAgent(1L, 2L);

        assertEquals(403, result.getCode());
    }

    @Test
    void uploadAvatar_shouldReturnPath() throws Exception {
        var file = new MockMultipartFile("file", "avatar.png", "image/png", "data".getBytes());
        when(agentService.uploadAvatar(eq(1L), any(), eq(1L)))
                .thenReturn(ApiResponse.success("uploads/avatar/1.png"));

        var result = controller.uploadAvatar(1L, file, 1L);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().contains("png"));
        verify(agentService).uploadAvatar(eq(1L), any(), eq(1L));
    }

    @Test
    void uploadAvatar_shouldRejectUnsupportedFileType() throws Exception {
        var file = new MockMultipartFile("file", "script.exe", "application/octet-stream", "bad".getBytes());
        when(agentService.uploadAvatar(eq(1L), any(), eq(1L)))
                .thenReturn(ApiResponse.error(400, "不支持的文件类型"));

        var result = controller.uploadAvatar(1L, file, 1L);

        assertEquals(400, result.getCode());
    }
}
