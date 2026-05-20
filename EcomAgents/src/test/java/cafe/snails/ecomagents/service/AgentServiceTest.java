package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.AiModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AgentService} 的单元测试，覆盖 Agent CRUD 业务逻辑及 workspace 同步。
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository repository;

    @Mock
    private AiModelRepository aiModelRepository;

    @Mock
    private WorkspaceInitService workspaceInitService;

    private AgentService service;

    private Agent sampleAgent;

    @BeforeEach
    void setUp() {
        service = new AgentService(repository, aiModelRepository, workspaceInitService);
        sampleAgent = Agent.builder()
                .id(1L).name("客服助手").icon("bi-headset")
                .description("客服Agent")
                .systemPrompt("你是一个客服助手")
                .greeting("你好！")
                .tags(List.of("对话")).tools(List.of("dialog"))
                .modelId(1L).status("active")
                .createdAt(LocalDate.of(2024, 1, 1)).createdBy(1L).build();
    }

    @Test
    void listAgents_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of(sampleAgent));
        ApiResponse<List<Agent>> result = service.listAgents();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void getAgent_existing_shouldReturn() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleAgent));
        ApiResponse<Agent> result = service.getAgent(1L);
        assertEquals(200, result.getCode());
        assertEquals("客服助手", result.getData().getName());
    }

    @Test
    void getAgent_notFound_shouldReturn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<Agent> result = service.getAgent(99L);
        assertEquals(404, result.getCode());
    }

    @Test
    void createAgent_shouldSetDefaultsAndInitWorkspace() {
        Agent input = Agent.builder().name("New Agent").modelId(1L).build();
        when(repository.save(any())).thenAnswer(i -> {
            Agent a = i.getArgument(0);
            a.setId(2L);
            return a;
        });
        ApiResponse<Agent> result = service.createAgent(input, 1L);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData().getCreatedAt());
        assertEquals("active", result.getData().getStatus());
        verify(workspaceInitService).initWorkspace(any());
    }

    @Test
    void updateAgent_shouldUpdateFields() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleAgent));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        Agent updates = Agent.builder().name("New Name").modelId(2L).build();
        ApiResponse<Agent> result = service.updateAgent(1L, updates, 1L);
        assertEquals("New Name", result.getData().getName());
        assertEquals(2L, result.getData().getModelId());
        assertEquals("bi-robot", result.getData().getIcon());
        // No prompt/knowledge change → workspace not updated
        verify(workspaceInitService, never()).updateAgentsMd(any(), any());
    }

    @Test
    void updateAgent_withSystemPrompt_shouldSyncAgentsMd() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleAgent));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        Agent updates = Agent.builder().systemPrompt("新的系统提示词").build();
        service.updateAgent(1L, updates, 1L);
        verify(workspaceInitService).updateAgentsMd(eq(1L), eq("新的系统提示词"));
    }

    @Test
    void updateAgent_notFound_shouldReturn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<Agent> result = service.updateAgent(99L, Agent.builder().build(), 1L);
        assertEquals(404, result.getCode());
    }

    @Test
    void deleteAgent_shouldDeleteAndCleanWorkspace() {
        when(repository.findById(1L)).thenReturn(Optional.of(sampleAgent));
        ApiResponse<Agent> result = service.deleteAgent(1L, 1L);
        assertEquals(200, result.getCode());
        verify(repository).delete(sampleAgent);
        verify(workspaceInitService).deleteWorkspace(1L);
    }

    @Test
    void deleteAgent_notFound_shouldReturn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        ApiResponse<Agent> result = service.deleteAgent(99L, 1L);
        assertEquals(404, result.getCode());
        verify(repository, never()).delete(any());
        verify(workspaceInitService, never()).deleteWorkspace(any());
    }
}
