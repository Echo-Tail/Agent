package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.PromptLibrary;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.PromptLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
/** 提示词库接口，负责提示词模板的查询、创建、更新、封面设置和删除。 */
@RestController
@RequestMapping("/v1/prompts")
@RequiredArgsConstructor
public class PromptLibraryController {

    private final PromptLibraryService promptLibraryService;

    /**
     * 分页查询提示词列表，支持分类筛选、创建者筛选、关键词模糊搜索。
     * <p>
     * createdBy — 筛选特定创建者；excludeUser — 排除特定用户（用于"他人创建的"场景）。
     * 两者互斥，同时传入时 createdBy 优先。
     * </p>
     */
    @GetMapping
    public ApiResponse<Page<PromptLibrary>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) Long excludeUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 排序由原生 SQL 的 ORDER BY p.created_at DESC 处理，不传入 Sort 避免字段名冲突
        var pageable = PageRequest.of(page, size);
        var result = promptLibraryService.list(category, createdBy, excludeUser, keyword, pageable);
        return ApiResponse.success(result);
    }

    /**
     * 获取单个提示词详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<PromptLibrary> getById(@PathVariable Long id) {
        return ApiResponse.success(promptLibraryService.getById(id));
    }

    /**
     * 创建提示词（图文分离：先创建，再单独设置封面）。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PromptLibrary> create(
            @RequestParam("prompt") String prompt,
            @RequestParam("category") String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "cover", required = false) MultipartFile cover,
            @CurrentUserId Long userId) {
        return promptLibraryService.create(prompt, category, tags, cover, userId);
    }

    /**
     * 更新提示词。
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PromptLibrary> update(
            @PathVariable Long id,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "cover", required = false) MultipartFile cover,
            @CurrentUserId Long userId) {
        return promptLibraryService.update(id, prompt, category, tags, cover, userId);
    }

    /**
     * 设置封面引用（从素材库或生图历史选择已有图片路径）。
     */
    @PutMapping("/{id}/cover-ref")
    public ApiResponse<PromptLibrary> setCoverRef(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        return promptLibraryService.setCoverRef(id, body.get("coverPath"), userId);
    }

    /**
     * 删除提示词。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        return promptLibraryService.delete(id, userId);
    }
}
