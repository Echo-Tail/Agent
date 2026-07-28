package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.image.ImageSessionDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 图片创作会话接口，管理会话元数据、工作区和画布内容。 */
@RestController
@RequestMapping("/v1/image-sessions")
@RequiredArgsConstructor
public class ImageSessionController {
    private final ImageSessionService service;

    /** 查询当前用户的图片创作会话。 */
    @GetMapping public ApiResponse<List<SessionResponse>> list(@CurrentUserId Long userId) { return ApiResponse.success(service.list(userId)); }
    /** 创建图片创作会话。 */
    @PostMapping public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.create(request, userId)); }
    /** 查询指定图片会话。 */
    @GetMapping("/{id}") public ApiResponse<SessionResponse> get(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.get(id, userId)); }
    /** 更新图片会话信息。 */
    @PatchMapping("/{id}") public ApiResponse<SessionResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.update(id, request, userId)); }
    /** 删除图片会话。 */
    @DeleteMapping("/{id}") public ApiResponse<SessionResponse> delete(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.delete(id, userId)); }
    /** 查询会话工作区信息。 */
    @GetMapping("/{id}/workspace") public ApiResponse<WorkspaceResponse> workspace(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.workspace(id, userId)); }
    /** 读取会话画布内容。 */
    @GetMapping("/{id}/canvas") public ApiResponse<CanvasResponse> getCanvas(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.getCanvas(id, userId)); }
    /** 保存会话画布内容。 */
    @PutMapping("/{id}/canvas") public ApiResponse<CanvasResponse> saveCanvas(@PathVariable Long id, @Valid @RequestBody SaveCanvasRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.saveCanvas(id, request, userId)); }
}
