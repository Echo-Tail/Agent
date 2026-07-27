package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.dto.review.ReviewProjectDtos.*;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.repository.review.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewProjectServiceTest {
    @Mock ReviewAnalysisProjectRepository projectRepository;
    @Mock ReviewProjectProductRepository productRepository;
    @Mock ReviewCollectionBatchRepository collectionRepository;
    @InjectMocks ReviewProjectService service;

    @BeforeEach
    void assignGeneratedIds() {
        lenient().when(projectRepository.save(any())).thenAnswer(invocation -> {
            ReviewAnalysisProject project = invocation.getArgument(0);
            if (project.getId() == null) project.setId(11L);
            return project;
        });
        lenient().when(productRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(collectionRepository.findFirstByProjectIdOrderByCreatedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
    }

    @Test
    void create_shouldOnlyRequireAsinsAndUseSystemDefaults() {
        var result = service.create(new CreateProjectRequest(List.of("b0aaaa1111", "B0BBBB2222")), 3L);

        assertNull(result.profileId());
        assertEquals("B0AAAA1111 等 2 个 ASIN", result.name());
        assertEquals("draft", result.status());
        assertEquals(List.of("B0AAAA1111", "B0BBBB2222"),
                result.products().stream().map(ProjectProductResponse::asin).toList());
        verify(productRepository).saveAll(argThat(products -> {
            for (ReviewProjectProduct product : products) {
                if (!"product".equals(product.getRole()) || product.getReviewLimit() != 200) return false;
            }
            return true;
        }));
    }

    @Test
    void create_shouldRejectInvalidOrDuplicateAsins() {
        assertThrows(BusinessException.class,
                () -> service.create(new CreateProjectRequest(List.of("bad")), 3L));
        assertThrows(BusinessException.class,
                () -> service.create(new CreateProjectRequest(List.of("B0AAAA1111", "b0aaaa1111")), 3L));
        verifyNoInteractions(projectRepository);
    }

    @Test
    void create_shouldNotLimitAsinCount() {
        var values = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> "B0" + String.format("%08d", index))
                .toList();

        var result = service.create(new CreateProjectRequest(values), 3L);

        assertEquals(12, result.products().size());
    }

    @Test
    void response_shouldExposeLatestCollectionForWorkflowRecovery() {
        var project = ReviewAnalysisProject.builder().id(11L).createdBy(3L).name("Task").status("collecting").build();
        when(projectRepository.findByIdAndCreatedBy(11L, 3L)).thenReturn(Optional.of(project));
        when(productRepository.findByProjectIdOrderById(11L)).thenReturn(List.of());
        when(collectionRepository.findFirstByProjectIdOrderByCreatedAtDesc(11L))
                .thenReturn(Optional.of(ReviewCollectionBatch.builder().id(31L).build()));

        assertEquals(31L, service.get(11L, 3L).latestCollectionId());
    }
}
