package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.model.Agent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WorkspaceInitService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceInitServiceTest {

    @Mock
    private WorkspaceConfig workspaceConfig;

    @TempDir
    Path tempDir;

    @Test
    void initWorkspace_shouldCreateDirectories() {
        WorkspaceInitService service = new WorkspaceInitService(workspaceConfig);
        Path agentDir = tempDir.resolve("agent-1");
        ReflectionTestUtils.setField(service, "workspaceConfig",
                new WorkspaceConfig() {{
                    setRoot(tempDir.toString());
                }});

        Agent agent = Agent.builder().id(1L).name("TestAgent").systemPrompt("You are a helpful assistant.").build();
        service.initWorkspace(agent);

        assertTrue(Files.exists(agentDir.resolve("AGENTS.md")));
        assertTrue(Files.exists(agentDir.resolve("MEMORY.md")));
        assertTrue(Files.exists(agentDir.resolve("sessions")));
        assertTrue(Files.exists(agentDir.resolve("knowledge")));
        assertTrue(Files.exists(agentDir.resolve("skills")));
        assertTrue(Files.exists(agentDir.resolve("subagents")));
    }

    @Test
    void initWorkspace_shouldBeIdempotent() {
        WorkspaceInitService service = new WorkspaceInitService(workspaceConfig);
        ReflectionTestUtils.setField(service, "workspaceConfig",
                new WorkspaceConfig() {{
                    setRoot(tempDir.toString());
                }});

        Agent agent = Agent.builder().id(1L).name("TestAgent").build();
        service.initWorkspace(agent);
        // Second call should not throw
        service.initWorkspace(agent);
        assertTrue(Files.exists(tempDir.resolve("agent-1/AGENTS.md")));
    }

    @Test
    void updateAgentsMd_shouldUpdateFile() throws Exception {
        WorkspaceInitService service = new WorkspaceInitService(workspaceConfig);
        ReflectionTestUtils.setField(service, "workspaceConfig",
                new WorkspaceConfig() {{
                    setRoot(tempDir.toString());
                }});

        Agent agent = Agent.builder().id(1L).name("TestAgent").build();
        service.initWorkspace(agent);

        // Update AGENTS.md
        service.updateAgentsMd(1L, "New system prompt");
        String content = Files.readString(tempDir.resolve("agent-1/AGENTS.md"));
        assertTrue(content.contains("New system prompt"));
    }

    @Test
    void deleteWorkspace_shouldRemoveDirectory() {
        WorkspaceInitService service = new WorkspaceInitService(workspaceConfig);
        ReflectionTestUtils.setField(service, "workspaceConfig",
                new WorkspaceConfig() {{
                    setRoot(tempDir.toString());
                }});

        Agent agent = Agent.builder().id(1L).name("TestAgent").build();
        service.initWorkspace(agent);
        assertTrue(Files.exists(tempDir.resolve("agent-1")));

        service.deleteWorkspace(1L);
        assertFalse(Files.exists(tempDir.resolve("agent-1")));
    }

    @Test
    void getAgentDir_shouldReturnCorrectPath() {
        WorkspaceConfig config = new WorkspaceConfig();
        config.setRoot("/tmp/workspace");
        WorkspaceInitService service = new WorkspaceInitService(config);
        Path path = service.getAgentDir(5L);
        assertEquals("/tmp/workspace/agent-5", path.toString().replace("\\", "/"));
    }
}
