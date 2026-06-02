package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.AgentSkill;
import cafe.snails.ecomagents.model.Skills;
import cafe.snails.ecomagents.repository.AgentSkillRepository;
import cafe.snails.ecomagents.repository.SkillsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SkillService} 单元测试。
 * <p>覆盖：技能列表 / Agent 绑定 / 删除 / 索引刷新。</p>
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private WorkspaceConfig workspaceConfig;
    @Mock
    private SkillConfig skillConfig;
    @Mock
    private SkillsRepository skillsRepository;
    @Mock
    private AgentSkillRepository agentSkillRepository;
    @Mock
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private SkillService skillService;

    @BeforeEach
    void setUp() {
        lenient().when(workspaceConfig.getRoot()).thenReturn(tempDir.toString());
        skillService = new SkillService(workspaceConfig, skillConfig, skillsRepository,
                agentSkillRepository, objectMapper);
    }

    // ==================== listSkills ====================

    @Test
    void listSkills_shouldReturnAll() {
        var skills = List.of(Skills.builder().name("web-search").description("Web search tool").build());
        when(skillsRepository.findAll()).thenReturn(skills);

        var result = skillService.listSkills();

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("web-search", result.getData().get(0).getName());
    }

    @Test
    void listSkills_shouldReturnEmptyWhenNone() {
        when(skillsRepository.findAll()).thenReturn(List.of());
        var result = skillService.listSkills();
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    // ==================== getSkillsForAgent ====================

    @Test
    void getSkillsForAgent_shouldReturnSkillNames() {
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("web-search").build(),
                AgentSkill.builder().agentId(1L).skillName("read-file").build()
        ));

        var names = skillService.getSkillsForAgent(1L);

        assertEquals(2, names.size());
        assertTrue(names.contains("web-search"));
        assertTrue(names.contains("read-file"));
    }

    @Test
    void getSkillsForAgent_shouldReturnEmptyWhenNone() {
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of());
        assertTrue(skillService.getSkillsForAgent(1L).isEmpty());
    }

    // ==================== deleteSkill ====================

    @Test
    void deleteSkill_shouldRejectWhenBoundAndNotForce() {
        when(agentSkillRepository.findBySkillName("web-search")).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("web-search").build()
        ));

        var result = skillService.deleteSkill("web-search", false);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("正在被 1 个 Agent 使用"));
        verify(skillsRepository, never()).delete(any());
    }

    @Test
    void deleteSkill_shouldForceDelete() {
        when(agentSkillRepository.findBySkillName("web-search")).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("web-search").build()
        ));
        when(skillsRepository.findByName("web-search")).thenReturn(Optional.of(
                Skills.builder().name("web-search").build()));

        var result = skillService.deleteSkill("web-search", true);

        assertEquals(200, result.getCode());
        verify(agentSkillRepository).delete(any());
        verify(skillsRepository).delete(any());
    }

    @Test
    void deleteSkill_shouldSucceedWhenNotBound() {
        when(agentSkillRepository.findBySkillName("unused-skill")).thenReturn(List.of());
        when(skillsRepository.findByName("unused-skill")).thenReturn(Optional.of(
                Skills.builder().name("unused-skill").build()));

        var result = skillService.deleteSkill("unused-skill", false);

        assertEquals(200, result.getCode());
        verify(skillsRepository).delete(any());
    }

    // ==================== syncAgentSkillsToWorkspace ====================

    @Test
    void syncAgentSkillsToWorkspace_shouldRemoveUnbound() {
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("old-skill").build()
        ));

        skillService.syncAgentSkillsToWorkspace(1L, List.of("new-skill"));

        // old-skill should be unbound
        verify(agentSkillRepository).delete(any(AgentSkill.class));
    }

    @Test
    void syncAgentSkillsToWorkspace_shouldKeepExisting() {
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("common-skill").build()
        ));

        skillService.syncAgentSkillsToWorkspace(1L, List.of("common-skill"));

        // Should not delete common-skill
        verify(agentSkillRepository, never()).delete(any());
    }

    @Test
    void syncAgentSkillsToWorkspace_withEmptyList_shouldRemoveAll() {
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of(
                AgentSkill.builder().agentId(1L).skillName("skill-a").build(),
                AgentSkill.builder().agentId(1L).skillName("skill-b").build()
        ));

        skillService.syncAgentSkillsToWorkspace(1L, List.of());

        verify(agentSkillRepository, times(2)).delete(any(AgentSkill.class));
    }

    // ==================== getSkillsDir ====================

    @Test
    void getSkillsDir_shouldReturnCorrectPath() {
        var path = skillService.getSkillsDir();
        assertTrue(path.toString().endsWith("skills"));
    }

    @Test
    void getAgentSkillsDir_shouldIncludeAgentId() {
        var path = skillService.getAgentSkillsDir(42L);
        assertTrue(path.toString().endsWith("agent-42\\skills")
                || path.toString().endsWith("agent-42/skills"));
    }

    // ==================== refreshIndex ====================

    @Test
    void refreshIndex_shouldNotThrowWhenDirNotExist() {
        // skills dir doesn't exist — should silently return
        skillService.refreshIndex();
        verify(skillsRepository, never()).save(any());
    }
}
