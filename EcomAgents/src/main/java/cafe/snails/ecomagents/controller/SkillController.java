package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.SkillUploadResult;
import cafe.snails.ecomagents.model.Skills;
import cafe.snails.ecomagents.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 技能控制器 — 全局技能池管理 + per-Agent 技能绑定查询。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /** 列出所有技能（全局池） */
    @GetMapping("/skills")
    public ApiResponse<List<Skills>> listSkills() {
        return skillService.listSkills();
    }

    /** 获取指定 Agent 绑定的技能名称列表 */
    @GetMapping("/agents/{agentId}/skills")
    public ApiResponse<List<String>> getAgentSkills(@PathVariable("agentId") Long agentId) {
        return ApiResponse.success(skillService.getSkillsForAgent(agentId));
    }

    /** 从 GitHub URL 导入技能 */
    @PostMapping("/skills/import-url")
    public ApiResponse<Void> importFromUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "请提供 GitHub 仓库 URL");
        }
        return skillService.importFromGithubUrl(url);
    }

    /** 上传 ZIP 文件导入技能（支持部分成功） */
    @PostMapping("/skills/upload")
    public ApiResponse<SkillUploadResult> uploadSkillZip(@RequestParam("file") MultipartFile file) {
        return skillService.uploadSkillZip(file);
    }

    /**
     * 删除技能。
     * @param name  技能名称
     * @param force 为 true 时强制删除（同时清理所有 Agent 引用），默认 false
     */
    @DeleteMapping("/skills/{name}")
    public ApiResponse<Void> deleteSkill(@PathVariable("name") String name,
                                         @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return skillService.deleteSkill(name, force);
    }
}
