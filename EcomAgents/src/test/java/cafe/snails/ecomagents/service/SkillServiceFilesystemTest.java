package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.SkillConfig;
import cafe.snails.ecomagents.config.WorkspaceConfig;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillServiceFilesystemTest {

    @Mock
    private WorkspaceConfig workspaceConfig;
    @Mock
    private SkillConfig skillConfig;
    @Mock
    private SkillsRepository skillsRepository;
    @Mock
    private AgentSkillRepository agentSkillRepository;

    @TempDir
    Path tempDir;

    private SkillService skillService;

    @BeforeEach
    void setUp() {
        lenient().when(workspaceConfig.getRoot()).thenReturn(tempDir.toString());
        skillService = new SkillService(workspaceConfig, skillConfig, skillsRepository,
                agentSkillRepository, new ObjectMapper());
    }

    @Test
    void syncAgentSkillsToWorkspace_shouldCopyNewSkillFromGlobalPool() throws Exception {
        Path globalSkill = skillService.getSkillsDir().resolve("new-skill");
        Files.createDirectories(globalSkill);
        Files.writeString(globalSkill.resolve("SKILL.md"), skillMarkdown("new-skill", "New skill"));
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of());

        skillService.syncAgentSkillsToWorkspace(1L, List.of("new-skill"));

        assertTrue(Files.exists(skillService.getAgentSkillsDir(1L).resolve("new-skill").resolve("SKILL.md")));
        verify(agentSkillRepository).save(argThat(binding ->
                binding.getAgentId().equals(1L) && binding.getSkillName().equals("new-skill")));
    }

    @Test
    void syncAgentSkillsToWorkspace_shouldDeleteRemovedSkillDirectory() throws Exception {
        Path oldSkill = skillService.getAgentSkillsDir(1L).resolve("old-skill");
        Files.createDirectories(oldSkill);
        Files.writeString(oldSkill.resolve("SKILL.md"), skillMarkdown("old-skill", "Old skill"));
        var binding = AgentSkill.builder().agentId(1L).skillName("old-skill").build();
        when(agentSkillRepository.findByAgentId(1L)).thenReturn(List.of(binding));

        skillService.syncAgentSkillsToWorkspace(1L, List.of());

        assertFalse(Files.exists(oldSkill));
        verify(agentSkillRepository).delete(binding);
    }

    @Test
    void refreshIndex_shouldParseSkillDirectoriesIntoRepository() throws Exception {
        Path skillDir = skillService.getSkillsDir().resolve("indexed-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), skillMarkdown("indexed-skill", "Indexed description"));
        when(skillsRepository.findByName("indexed-skill")).thenReturn(Optional.empty());

        skillService.refreshIndex();

        verify(skillsRepository).save(argThat(skill ->
                skill.getName().equals("indexed-skill")
                        && skill.getDescription().equals("Indexed description")
                        && skill.getCategory().equals("productivity")));
    }

    @Test
    void refreshIndex_shouldSkipExistingSkills() throws Exception {
        Path skillDir = skillService.getSkillsDir().resolve("existing-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), skillMarkdown("existing-skill", "Existing"));
        when(skillsRepository.findByName("existing-skill"))
                .thenReturn(Optional.of(Skills.builder().name("existing-skill").build()));

        skillService.refreshIndex();

        verify(skillsRepository, never()).save(any());
    }

    @Test
    void uploadSkillZip_shouldRejectEmptyFile() {
        var file = new MockMultipartFile("file", "skills.zip", "application/zip", new byte[0]);

        var result = skillService.uploadSkillZip(file);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("上传文件为空"));
    }

    @Test
    void uploadSkillZip_shouldRejectNonZipFilename() {
        var file = new MockMultipartFile("file", "skills.txt", "text/plain", "data".getBytes());

        var result = skillService.uploadSkillZip(file);

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("ZIP"));
    }

    @Test
    void uploadSkillZip_shouldImportValidSkillAndReportInvalidSkill() throws Exception {
        var file = new MockMultipartFile("file", "skills.zip", "application/zip", zipBytes(
                entry("good-skill/SKILL.md", skillMarkdown("good-skill", "Good description")),
                entry("bad-skill/README.md", "missing skill file")
        ));
        when(skillsRepository.findByName("good-skill")).thenReturn(Optional.empty());

        var result = skillService.uploadSkillZip(file);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().getSuccessCount());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(List.of("good-skill"), result.getData().getImported());
        assertEquals("bad-skill", result.getData().getFailed().get(0).getName());
        assertTrue(Files.exists(skillService.getSkillsDir().resolve("good-skill").resolve("SKILL.md")));
        verify(skillsRepository).save(argThat(skill ->
                skill.getName().equals("good-skill")
                        && skill.getDescription().equals("Good description")
                        && skill.getCategory().equals("productivity")));
    }

    @Test
    void uploadSkillZip_shouldRejectZipSlipEntriesButContinueSafeEntries() throws Exception {
        var file = new MockMultipartFile("file", "skills.zip", "application/zip", zipBytes(
                entry("../evil.txt", "evil"),
                entry("safe-skill/SKILL.md", skillMarkdown("safe-skill", "Safe description"))
        ));
        when(skillsRepository.findByName("safe-skill")).thenReturn(Optional.empty());

        var result = skillService.uploadSkillZip(file);

        assertEquals(200, result.getCode());
        assertEquals(List.of("safe-skill"), result.getData().getImported());
        assertFalse(Files.exists(tempDir.resolve("evil.txt")));
    }

    private static String skillMarkdown(String name, String description) {
        return """
                ---
                name: %s
                description: %s
                category: productivity
                ---
                Body
                """.formatted(name, description);
    }

    private static ZipItem entry(String name, String content) {
        return new ZipItem(name, content);
    }

    private static byte[] zipBytes(ZipItem... items) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipItem item : items) {
                zip.putNextEntry(new ZipEntry(item.name));
                zip.write(item.content.getBytes());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private record ZipItem(String name, String content) {
    }
}
