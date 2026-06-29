package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.VisualStrategyGenerateRequest;
import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import cafe.snails.ecomagents.service.ProductProfileService;
import cafe.snails.ecomagents.service.SellingPointCognitionService;
import cafe.snails.ecomagents.service.VisualStrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductProfileControllerVisualStrategyTest {

    @Mock private ProductProfileService productProfileService;
    @Mock private SellingPointCognitionService sellingPointCognitionService;
    @Mock private VisualStrategyService visualStrategyService;

    private ProductProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductProfileController(productProfileService, sellingPointCognitionService, visualStrategyService);
    }

    @Test
    void generateVisualStrategy_shouldDelegateToServiceWithRequestBody() {
        ProductVisualStrategyVersion version = version(88L);
        VisualStrategyGenerateRequest request = new VisualStrategyGenerateRequest(77L, List.of("gallery", "aplus"));
        when(visualStrategyService.generate(10L, 77L, List.of("gallery", "aplus"), 7L)).thenReturn(version);

        ApiResponse<ProductVisualStrategyVersion> response = controller.generateVisualStrategy(10L, request, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(visualStrategyService).generate(10L, 77L, List.of("gallery", "aplus"), 7L);
    }

    @Test
    void generateVisualStrategy_shouldAllowMissingBodyForDefaultScopeAndLatestCognition() {
        ProductVisualStrategyVersion version = version(88L);
        when(visualStrategyService.generate(10L, null, null, 7L)).thenReturn(version);

        ApiResponse<ProductVisualStrategyVersion> response = controller.generateVisualStrategy(10L, null, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(visualStrategyService).generate(10L, null, null, 7L);
    }

    @Test
    void getCurrentVisualStrategy_shouldDelegateToService() {
        ProductVisualStrategyVersion version = version(88L);
        when(visualStrategyService.getCurrent(10L, 7L)).thenReturn(version);

        ApiResponse<ProductVisualStrategyVersion> response = controller.getCurrentVisualStrategy(10L, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(visualStrategyService).getCurrent(10L, 7L);
    }

    @Test
    void getVisualStrategyVersions_shouldDelegateToService() {
        List<ProductVisualStrategyVersion> versions = List.of(version(88L));
        when(visualStrategyService.listVersions(10L, 7L)).thenReturn(versions);

        ApiResponse<List<ProductVisualStrategyVersion>> response = controller.getVisualStrategyVersions(10L, 7L);

        assertEquals(200, response.getCode());
        assertSame(versions, response.getData());
        verify(visualStrategyService).listVersions(10L, 7L);
    }

    @Test
    void updateVisualStrategy_shouldDelegateToService() {
        ProductVisualStrategyVersion version = version(88L);
        String json = "{\"content_scope\":[\"gallery\"],\"gallery_strategy\":{\"images\":[]}}";
        when(visualStrategyService.update(10L, 88L, json, 7L)).thenReturn(version);

        ApiResponse<ProductVisualStrategyVersion> response = controller.updateVisualStrategy(10L, 88L, json, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(visualStrategyService).update(10L, 88L, json, 7L);
    }

    @Test
    void confirmVisualStrategy_shouldDelegateToService() {
        ProductVisualStrategyVersion version = version(88L);
        when(visualStrategyService.confirm(10L, 88L, 7L)).thenReturn(version);

        ApiResponse<ProductVisualStrategyVersion> response = controller.confirmVisualStrategy(10L, 88L, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(visualStrategyService).confirm(10L, 88L, 7L);
    }

    private ProductVisualStrategyVersion version(Long id) {
        return ProductVisualStrategyVersion.builder()
                .id(id)
                .profileId(10L)
                .cognitionVersionId(77L)
                .versionNumber(1)
                .status("DRAFT")
                .contentScope("gallery+aplus")
                .strategyJson("{\"content_scope\":[\"gallery\"],\"gallery_strategy\":{\"images\":[]}}")
                .build();
    }
}