package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewProjectDtos.*;
import cafe.snails.ecomagents.exception.*;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReviewProjectService {
    static final String MARKETPLACE = "amazon_us";
    static final String CATEGORY = "car_stereo";
    static final String STATUS_DRAFT = "draft";
    static final int DEFAULT_REVIEW_LIMIT = 200;
    private static final Set<String> MUTABLE_STATUSES = Set.of("draft", "failed");

    private final ReviewAnalysisProjectRepository projectRepository;
    private final ReviewProjectProductRepository productRepository;
    private final ReviewCollectionBatchRepository collectionRepository;

    public List<ProjectResponse> list(Long userId) {
        return projectRepository.findByCreatedByOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse).toList();
    }

    public ProjectResponse get(Long projectId, Long userId) {
        return toResponse(requireOwned(projectId, userId));
    }

    @Transactional
    public ProjectResponse update(Long projectId, UpdateProjectRequest request, Long userId) {
        var project = requireOwned(projectId, userId);
        project.setName(request.name().trim());
        project.setUpdatedAt(LocalDateTime.now());
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long projectId, Long userId) {
        var project = requireOwned(projectId, userId);
        requireMutable(project);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, Long userId) {
        var asins = validateAsins(request.asins());
        var now = LocalDateTime.now();
        var project = projectRepository.save(ReviewAnalysisProject.builder()
                .name(defaultName(asins))
                .marketplace(MARKETPLACE)
                .category(CATEGORY)
                .status(STATUS_DRAFT)
                .createdBy(userId)
                .createdAt(now)
                .updatedAt(now)
                .build());
        var products = asins.stream().map(asin -> ReviewProjectProduct.builder()
                .projectId(project.getId())
                .asin(asin)
                .role("product")
                .reviewLimit(DEFAULT_REVIEW_LIMIT)
                .createdAt(now)
                .build()).toList();
        productRepository.saveAll(products);
        return toResponse(project, products);
    }

    @Transactional
    public ProjectResponse replaceProducts(Long projectId, List<ProjectProductRequest> products, Long userId) {
        var project = requireOwned(projectId, userId);
        requireMutable(project);
        validateProducts(products);
        productRepository.deleteByProjectId(projectId);
        var replacements = products.stream().map(input -> ReviewProjectProduct.builder()
                .projectId(projectId)
                .asin(normalizeAsin(input.asin()))
                .role(input.role())
                .reviewLimit(input.reviewLimit())
                .createdAt(LocalDateTime.now())
                .build()).toList();
        productRepository.saveAll(replacements);
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
        return toResponse(project, replacements);
    }

    ReviewAnalysisProject requireOwned(Long projectId, Long userId) {
        return projectRepository.findByIdAndCreatedBy(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "评论分析项目不存在"));
    }

    void requireMutable(ReviewAnalysisProject project) {
        if (!MUTABLE_STATUSES.contains(project.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前项目状态不允许修改商品");
        }
    }

    private void validateProducts(List<ProjectProductRequest> products) {
        if (products == null || products.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目必须至少包含一个商品");
        }
        long ownCount = products.stream().filter(value -> "own".equals(value.role())).count();
        if (ownCount != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目必须且只能包含一个本品 ASIN");
        }
        var asins = new HashSet<String>();
        for (var product : products) {
            String asin = normalizeAsin(product.asin());
            if (!asin.matches("[A-Z0-9]{10}")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ASIN 必须为 10 位字母或数字");
            }
            if (!Set.of("own", "competitor").contains(product.role())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "商品角色无效");
            }
            if (product.reviewLimit() == null || product.reviewLimit() < 100 || product.reviewLimit() > 500) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "评论数量必须在 100 到 500 之间");
            }
            if (!asins.add(asin)) {
                throw new BusinessException(ErrorCode.CONFLICT, "项目中不能包含重复 ASIN");
            }
        }
    }

    private String normalizeAsin(String asin) {
        return asin == null ? "" : asin.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> validateAsins(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请至少输入一个 ASIN");
        }
        var unique = new LinkedHashSet<String>();
        for (String value : values) {
            String asin = normalizeAsin(value);
            if (!asin.matches("[A-Z0-9]{10}")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "ASIN 必须为 10 位字母或数字");
            }
            if (!unique.add(asin)) {
                throw new BusinessException(ErrorCode.CONFLICT, "不能输入重复 ASIN");
            }
        }
        return List.copyOf(unique);
    }

    private String defaultName(List<String> asins) {
        return asins.size() == 1 ? asins.get(0) + " 评论分析"
                : asins.get(0) + " 等 " + asins.size() + " 个 ASIN";
    }

    private ProjectResponse toResponse(ReviewAnalysisProject project) {
        return toResponse(project, productRepository.findByProjectIdOrderById(project.getId()));
    }

    private ProjectResponse toResponse(ReviewAnalysisProject project, List<ReviewProjectProduct> products) {
        var productResponses = products.stream().map(value -> new ProjectProductResponse(
                value.getId(), value.getAsin(), value.getRole(), value.getProductName(), value.getReviewLimit())).toList();
        Long latestCollectionId = collectionRepository.findFirstByProjectIdOrderByCreatedAtDesc(project.getId())
                .map(ReviewCollectionBatch::getId).orElse(null);
        return new ProjectResponse(project.getId(), project.getProfileId(), project.getName(),
                project.getMarketplace(), project.getCategory(), project.getStatus(), latestCollectionId, productResponses,
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
