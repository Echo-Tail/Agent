package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.model.Agent;
import cafe.snails.ecomagents.model.ToolConfig;
import cafe.snails.ecomagents.repository.AgentRepository;
import cafe.snails.ecomagents.repository.ToolConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * Agent 工具可用性服务测试，验证网页搜索工具绑定、启用和配置判断。
 */
class AgentToolAvailabilityServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private ToolConfigRepository toolConfigRepository;

    private AgentToolAvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AgentToolAvailabilityService(agentRepository, toolConfigRepository, new ObjectMapper());
    }

    @Test
    void getWebSearchAvailability_shouldBeAvailableWhenBoundEnabledAndConfigured() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent(List.of("web_search"))));
        when(toolConfigRepository.findById("web_search")).thenReturn(Optional.of(
                ToolConfig.builder().id("web_search").enabled(true).configJson("{\"apiKey\":\"tvly-key\"}").build()));

        var result = service.getWebSearchAvailability(1L);

        assertTrue(result.isBoundToAgent());
        assertTrue(result.isGloballyEnabled());
        assertTrue(result.isConfigured());
        assertTrue(result.isAvailable());
    }

    @Test
    void getWebSearchAvailability_shouldRejectUnboundAgent() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent(List.of())));
        when(toolConfigRepository.findById("web_search")).thenReturn(Optional.of(
                ToolConfig.builder().id("web_search").enabled(true).configJson("{\"apiKey\":\"tvly-key\"}").build()));

        var result = service.getWebSearchAvailability(1L);

        assertFalse(result.isBoundToAgent());
        assertFalse(result.isAvailable());
    }

    @Test
    void getWebSearchAvailability_shouldRejectMissingApiKey() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent(List.of("web_search"))));
        when(toolConfigRepository.findById("web_search")).thenReturn(Optional.of(
                ToolConfig.builder().id("web_search").enabled(true).configJson("{}").build()));

        var result = service.getWebSearchAvailability(1L);

        assertTrue(result.isBoundToAgent());
        assertTrue(result.isGloballyEnabled());
        assertFalse(result.isConfigured());
        assertFalse(result.isAvailable());
    }

    @Test
    void getWebSearchAvailability_shouldTreatSystemAgentAsBoundToEnabledConfiguredTool() {
        Agent systemAgent = agent(List.of());
        systemAgent.setIsSystem(true);
        when(agentRepository.findById(1L)).thenReturn(Optional.of(systemAgent));
        when(toolConfigRepository.findById("web_search")).thenReturn(Optional.of(
                ToolConfig.builder().id("web_search").enabled(true).configJson("{\"apiKey\":\"tvly-key\"}").build()));

        var result = service.getWebSearchAvailability(1L);

        assertTrue(result.isBoundToAgent());
        assertTrue(result.isAvailable());
    }

    private Agent agent(List<String> tools) {
        return Agent.builder()
                .id(1L)
                .name("agent")
                .tools(tools)
                .build();
    }
}
