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

/** 亚马逊商品图片任务接口，负责素材分析、提示词维护、图片生成及结果确认。 */
@RestController
@RequestMapping("/v1/amazon-image-tasks")
@RequiredArgsConstructor
public class AmazonImageTaskController {

    private final AmazonImageTaskService taskService;

    /** 分页查询当前用户的亚马逊图片任务。 */
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

    /** 创建亚马逊图片任务并保存初始素材。 */
    @PostMapping
    public ApiResponse<AmazonImageTask> create(
            @RequestBody AmazonImageTaskService.CreateTaskRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("任务已创建", taskService.create(request, userId));
    }

    /** 查询指定任务详情。 */
    @GetMapping("/{id}")
    public ApiResponse<AmazonImageTask> get(@PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(taskService.get(id, userId));
    }

    /** 更新任务的商品事实信息。 */
    @PutMapping("/{id}/facts")
    public ApiResponse<AmazonImageTask> updateFacts(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdateFactsRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("产品事实已保存", taskService.updateFacts(id, request, userId));
    }

    /** 更新任务使用的图片生成提示词。 */
    @PutMapping("/{id}/prompt")
    public ApiResponse<AmazonImageTask> updatePrompt(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdatePromptRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("提示词已保存", taskService.updatePrompt(id, request, userId));
    }

    /** 调用模型分析商品图片的视觉表达。 */
    @PostMapping("/{id}/analyze-expression")
    public ApiResponse<AmazonImageTask> analyzeExpression(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.AnalyzeExpressionRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("图片表达结构已保存", taskService.analyzeExpression(id, request, userId));
    }

    /** 更新从商品素材中提取的事实信息。 */
    @PutMapping("/{id}/material-facts")
    public ApiResponse<AmazonImageTask> updateMaterialFacts(
            @PathVariable Long id,
            @RequestBody AmazonImageTaskService.UpdateMaterialFactsRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("素材事实已保存", taskService.updateMaterialFacts(id, request, userId));
    }

    /** 根据任务配置提交图片生成作业。 */
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

    /** 查询任务已生成的图片结果。 */
    @GetMapping("/{id}/results")
    public ApiResponse<List<AmazonImageResult>> getResults(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success(taskService.getResults(id, userId));
    }

    /** 标记生成结果的采用状态。 */
    @PutMapping("/results/{resultId}/status")
    public ApiResponse<AmazonImageResult> markResult(
            @PathVariable Long resultId,
            @RequestBody AmazonImageTaskService.MarkResultRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success("结果状态已更新", taskService.markResult(resultId, request.status(), userId));
    }

    /** 将任务标记为已完成。 */
    @PostMapping("/{id}/complete")
    public ApiResponse<AmazonImageTask> completeTask(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ApiResponse.success("任务已完成", taskService.completeTask(id, userId));
    }

    /** 删除指定任务及其关联数据。 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        taskService.delete(id, userId);
        return ApiResponse.success("任务已删除", null);
    }
}
