package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
import cafe.snails.ecomagents.model.Skills;
import cafe.snails.ecomagents.repository.AgentSkillRepository;
import cafe.snails.ecomagents.repository.SkillsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link SkillService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private SkillsRepository skillsRepository;
    @Mock
    private AgentSkillRepository agentSkillRepository;

    private WorkspaceConfig workspaceConfig;
    private SkillConfig skillConfig;
    private SkillService skillService;

    @Captor
    private ArgumentCaptor<Skills> skillsCaptor;

    @BeforeEach
    void setUp() {
        workspaceConfig = new WorkspaceConfig();
        workspaceConfig.setRoot(tempDir.toString());
        skillConfig = new SkillConfig();
        skillConfig.setGhProxyUrl("https://gh-proxy.org");
        skillService = new SkillService(workspaceConfig, skillConfig, skillsRepository, agentSkillRepository, new ObjectMapper());
    }

    @Test
    void getSkillsDir_shouldReturnWorkspaceSkills() {
        assertEquals(tempDir.resolve("skills"), skillService.getSkillsDir());
    }

    @Test
    void listSkills_shouldReturnEmpty_whenNoSkills() {
        when(skillsRepository.findAll()).thenReturn(Collections.emptyList());
        var result = skillService.listSkills();
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void listSkills_shouldReturnAllFromRepository() {
        Skills skill = Skills.builder().name("test-skill").description("Test").build();
        when(skillsRepository.findAll()).thenReturn(List.of(skill));
        var result = skillService.listSkills();
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("test-skill", result.getData().get(0).getName());
    }

    @Test
    void deleteSkill_shouldReturn400_whenUsedByAgents() {
        when(agentSkillRepository.findBySkillName("used-skill")).thenReturn(
                List.of(cafe.snails.ecomagents.model.AgentSkill.builder()
                        .agentId(1L).skillName("used-skill").build()));
        var result = skillService.deleteSkill("used-skill", false);
        assertEquals(400, result.getCode());
        verify(skillsRepository, never()).delete(any());
    }

    @Test
    void deleteSkill_shouldDeleteSkillDirAndDb() throws Exception {
        Path skillDir = skillService.getSkillsDir().resolve("test-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: test-skill\ndescription: Test\n---\n\nContent");

        Skills skill = Skills.builder().name("test-skill").description("Test").build();
        when(skillsRepository.findByName("test-skill")).thenReturn(Optional.of(skill));

        var result = skillService.deleteSkill("test-skill", false);
        assertEquals(200, result.getCode());
        assertFalse(Files.exists(skillDir));
        verify(skillsRepository).delete(skill);
    }

    @Test
    void deleteSkill_shouldForceDeleteAgentBindings() throws Exception {
        Path skillDir = skillService.getSkillsDir().resolve("forced-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: forced-skill\n---\n\nContent");

        var binding = cafe.snails.ecomagents.model.AgentSkill.builder()
                .agentId(1L).skillName("forced-skill").build();
        when(agentSkillRepository.findBySkillName("forced-skill")).thenReturn(List.of(binding));
        when(skillsRepository.findByName("forced-skill")).thenReturn(
                Optional.of(Skills.builder().name("forced-skill").build()));

        var result = skillService.deleteSkill("forced-skill", true);
        assertEquals(200, result.getCode());
        assertFalse(Files.exists(skillDir));
        verify(agentSkillRepository).delete(binding);
        verify(skillsRepository).delete(any());
    }

    @Test
    void refreshIndex_shouldSyncFsToDb() throws Exception {
        Files.createDirectories(skillService.getSkillsDir().resolve("skill-a"));
        Files.writeString(
                skillService.getSkillsDir().resolve("skill-a").resolve("SKILL.md"),
                "---\nname: skill-a\ndescription: First skill\ncategory: development\n---\n\nContent");

        Files.createDirectories(skillService.getSkillsDir().resolve("skill-b"));
        Files.writeString(
                skillService.getSkillsDir().resolve("skill-b").resolve("SKILL.md"),
                "---\nname: skill-b\ndescription: Skill B\ncategory: data\n---\n\nContent");

        when(skillsRepository.findByName("skill-a")).thenReturn(Optional.empty());
        when(skillsRepository.findByName("skill-b")).thenReturn(Optional.empty());

        skillService.refreshIndex();

        verify(skillsRepository, times(2)).save(any());
    }

    @Test
    void refreshIndex_shouldSkipExistingEntries() throws Exception {
        Files.createDirectories(skillService.getSkillsDir().resolve("existing-skill"));
        Files.writeString(
                skillService.getSkillsDir().resolve("existing-skill").resolve("SKILL.md"),
                "---\nname: existing-skill\ndescription: Existing\n---\n\nContent");

        Skills existing = Skills.builder().name("existing-skill").description("Existing").build();
        when(skillsRepository.findByName("existing-skill")).thenReturn(Optional.of(existing));

        skillService.refreshIndex();

        verify(skillsRepository, never()).save(any());
    }

    @Test
    void refreshIndex_shouldHandleMissingSkillsDir() {
        // skills dir does not exist — should be a no-op
        skillService.refreshIndex();
        verify(skillsRepository, never()).findByName(any());
    }

    @Test
    void deleteSkill_shouldDeleteDirectoryRecursively() throws Exception {
        Path skillDir = skillService.getSkillsDir().resolve("nested-skill");
        Files.createDirectories(skillDir.resolve("subdir"));
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: nested-skill\n---\n\nContent");
        Files.writeString(skillDir.resolve("subdir").resolve("file.txt"), "data");

        when(skillsRepository.findByName("nested-skill")).thenReturn(
                Optional.of(Skills.builder().name("nested-skill").build()));

        var result = skillService.deleteSkill("nested-skill", false);
        assertEquals(200, result.getCode());
        assertFalse(Files.exists(skillDir));
    }
}
