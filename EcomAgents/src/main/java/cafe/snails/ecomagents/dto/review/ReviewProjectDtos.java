package cafe.snails.ecomagents.dto.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论分析项目相关的数据传输对象集合。
 */
public final class ReviewProjectDtos {
    private ReviewProjectDtos() {}

    /** 使用商品 ASIN 列表创建评论分析项目的请求。 */
    public record CreateProjectRequest(
            @NotEmpty
            List<@NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{10}$") String> asins) {}

    /** 更新评论分析项目名称的请求。 */
    public record UpdateProjectRequest(
            @NotBlank @Size(max = 200) String name) {}

    /** 配置项目内商品及采集范围的请求。 */
    public record ProjectProductRequest(
            @NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{10}$") String asin,
            @NotBlank @Pattern(regexp = "^(own|competitor)$") String role,
            @NotNull @Min(100) @Max(500) Integer reviewLimit) {}

    /** 项目关联商品的响应。 */
    public record ProjectProductResponse(
            Long id, String asin, String role, String productName, Integer reviewLimit) {}

    /** 评论分析项目详情响应。 */
    public record ProjectResponse(
            Long id, Long profileId, String name, String marketplace, String category,
            String status, Long latestCollectionId, List<ProjectProductResponse> products,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
