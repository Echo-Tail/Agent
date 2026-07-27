package cafe.snails.ecomagents.dto.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewProjectDtos {
    private ReviewProjectDtos() {}

    public record CreateProjectRequest(
            @NotEmpty
            List<@NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{10}$") String> asins) {}

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 200) String name) {}

    public record ProjectProductRequest(
            @NotBlank @Pattern(regexp = "(?i)^[A-Z0-9]{10}$") String asin,
            @NotBlank @Pattern(regexp = "^(own|competitor)$") String role,
            @NotNull @Min(100) @Max(500) Integer reviewLimit) {}

    public record ProjectProductResponse(
            Long id, String asin, String role, String productName, Integer reviewLimit) {}

    public record ProjectResponse(
            Long id, Long profileId, String name, String marketplace, String category,
            String status, Long latestCollectionId, List<ProjectProductResponse> products,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
