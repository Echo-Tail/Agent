package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.image.ImageSessionDtos.*;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.ImageSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/image-sessions")
@RequiredArgsConstructor
public class ImageSessionController {
    private final ImageSessionService service;

    @GetMapping public ApiResponse<List<SessionResponse>> list(@CurrentUserId Long userId) { return ApiResponse.success(service.list(userId)); }
    @PostMapping public ApiResponse<SessionResponse> create(@Valid @RequestBody CreateRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.create(request, userId)); }
    @GetMapping("/{id}") public ApiResponse<SessionResponse> get(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.get(id, userId)); }
    @PatchMapping("/{id}") public ApiResponse<SessionResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.update(id, request, userId)); }
    @DeleteMapping("/{id}") public ApiResponse<SessionResponse> delete(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.delete(id, userId)); }
    @GetMapping("/{id}/workspace") public ApiResponse<WorkspaceResponse> workspace(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.workspace(id, userId)); }
    @GetMapping("/{id}/canvas") public ApiResponse<CanvasResponse> getCanvas(@PathVariable Long id, @CurrentUserId Long userId) { return ApiResponse.success(service.getCanvas(id, userId)); }
    @PutMapping("/{id}/canvas") public ApiResponse<CanvasResponse> saveCanvas(@PathVariable Long id, @Valid @RequestBody SaveCanvasRequest request, @CurrentUserId Long userId) { return ApiResponse.success(service.saveCanvas(id, request, userId)); }
}
