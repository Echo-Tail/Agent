package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.SkillIndex;
import cafe.snails.ecomagents.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 技能控制器 — 基于文件系统的技能管理。
 * 仅提供列表、GitHub URL 导入、ZIP 上传、删除。
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /** 列出所有技能 */
    @GetMapping("/skills")
    public ApiResponse<List<SkillIndex>> listSkills() {
        return skillService.listSkills();
    }

    /**
     * 从 GitHub URL 导入技能。
     * body: { "url": "https://github.com/{owner}/{repo}" }
     * 也支持 tree 路径：https://github.com/{owner}/{repo}/tree/{branch}/skills/{name}
     */
    @PostMapping("/skills/import-url")
    public ApiResponse<Void> importFromUrl(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "请提供 GitHub 仓库 URL");
        }
        return skillService.importFromGithubUrl(url);
    }

    /**
     * 上传 ZIP 文件导入技能。
     */
    @PostMapping("/skills/upload")
    public ApiResponse<Void> uploadSkillZip(@RequestParam("file") MultipartFile file) {
        return skillService.uploadSkillZip(file);
    }

    /** 删除技能 */
    @DeleteMapping("/skills/{name}")
    public ApiResponse<Void> deleteSkill(@PathVariable("name") String name) {
        return skillService.deleteSkill(name);
    }
}
