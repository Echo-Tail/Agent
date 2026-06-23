package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.AmazonImageResult;
import cafe.snails.ecomagents.model.AmazonImageTask;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.AmazonImageTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/amazon-image-tasks")
@RequiredArgsConstructor
public class AmazonImageTaskController {

    private final AmazonImageTaskService taskService;

    @GetMapping
    public ApiResponse<Page<AmazonImageTask>> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String asin,
            @RequestParam(required = false) String imageType,
            @RequestParam(required = false) String status) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ApiResponse.success(taskService.list(userId, asin, imageType, status, pageable));
    }

    @PostMapping
    public ApiResponse<AmazonImageTask> create(
            @RequestBody AmazonImageTaskService.CreateTaskRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("任务已创建", taskService.create(request, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AmazonImageTask> get(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(taskService.get(id, userId));
    }

    @PutMapping("/{id}/facts")
    public ApiResponse<AmazonImageTask> updateFacts(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdateFactsRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("产品事实已保存", taskService.updateFacts(id, request, userId));
    }

    @PutMapping("/{id}/prompt")
    public ApiResponse<AmazonImageTask> updatePrompt(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdatePromptRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("提示词已保存", taskService.updatePrompt(id, request, userId));
    }

    @PostMapping("/{id}/analyze-expression")
    public ApiResponse<AmazonImageTask> analyzeExpression(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.AnalyzeExpressionRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("图片表达结构已保存", taskService.analyzeExpression(id, request, userId));
    }

    @PutMapping("/{id}/material-facts")
    public ApiResponse<AmazonImageTask> updateMaterialFacts(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdateMaterialFactsRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("素材事实已保存", taskService.updateMaterialFacts(id, request, userId));
    }

    @PostMapping("/{id}/generate")
    public ApiResponse<AmazonImageTaskService.GenerateTaskResult> generate(
            @PathVariable Long id,
            @RequestParam(required = false) String prompt,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String quality,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false, defaultValue = "1") int n,
            @RequestParam(required = false) Long modelId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(taskService.generate(id, prompt, size, quality, images, n, modelId, userId));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<List<AmazonImageResult>> getResults(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(taskService.getResults(id, userId));
    }

    @PutMapping("/results/{resultId}/status")
    public ApiResponse<AmazonImageResult> markResult(
            @PathVariable Long resultId,
            @RequestBody AmazonImageTaskService.MarkResultRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("结果状态已更新", taskService.markResult(resultId, request.status(), userId));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AmazonImageTask> completeTask(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("任务已完成", taskService.completeTask(id, userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        taskService.delete(id, userId);
        return ApiResponse.success("任务已删除", null);
    }
}
