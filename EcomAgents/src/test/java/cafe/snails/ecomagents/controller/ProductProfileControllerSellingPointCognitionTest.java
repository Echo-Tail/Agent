package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
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
class ProductProfileControllerSellingPointCognitionTest {

    @Mock private ProductProfileService productProfileService;
    @Mock private SellingPointCognitionService sellingPointCognitionService;
    @Mock private VisualStrategyService visualStrategyService;

    private ProductProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductProfileController(productProfileService, sellingPointCognitionService, visualStrategyService);
    }

    @Test
    void generateSellingPointCognitions_shouldDelegateToService() {
        ProductSellingPointCognitionVersion version = version(1L);
        when(sellingPointCognitionService.generate(10L, 7L)).thenReturn(version);

        ApiResponse<ProductSellingPointCognitionVersion> response = controller.generateSellingPointCognitions(10L, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(sellingPointCognitionService).generate(10L, 7L);
    }

    @Test
    void getCurrentSellingPointCognition_shouldDelegateToService() {
        ProductSellingPointCognitionVersion version = version(1L);
        when(sellingPointCognitionService.getCurrent(10L, 7L)).thenReturn(version);

        ApiResponse<ProductSellingPointCognitionVersion> response = controller.getCurrentSellingPointCognition(10L, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(sellingPointCognitionService).getCurrent(10L, 7L);
    }

    @Test
    void getSellingPointCognitionVersions_shouldDelegateToService() {
        List<ProductSellingPointCognitionVersion> versions = List.of(version(1L), version(2L));
        when(sellingPointCognitionService.listVersions(10L, 7L)).thenReturn(versions);

        ApiResponse<List<ProductSellingPointCognitionVersion>> response = controller.getSellingPointCognitionVersions(10L, 7L);

        assertEquals(200, response.getCode());
        assertSame(versions, response.getData());
        verify(sellingPointCognitionService).listVersions(10L, 7L);
    }

    @Test
    void updateSellingPointCognition_shouldDelegateToService() {
        ProductSellingPointCognitionVersion version = version(99L);
        String json = "{\"buyer_cognitions\":[]}";
        when(sellingPointCognitionService.update(10L, 99L, json, 7L)).thenReturn(version);

        ApiResponse<ProductSellingPointCognitionVersion> response = controller.updateSellingPointCognition(10L, 99L, json, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(sellingPointCognitionService).update(10L, 99L, json, 7L);
    }

    @Test
    void confirmSellingPointCognition_shouldDelegateToService() {
        ProductSellingPointCognitionVersion version = version(99L);
        when(sellingPointCognitionService.confirm(10L, 99L, 7L)).thenReturn(version);

        ApiResponse<ProductSellingPointCognitionVersion> response = controller.confirmSellingPointCognition(10L, 99L, 7L);

        assertEquals(200, response.getCode());
        assertSame(version, response.getData());
        verify(sellingPointCognitionService).confirm(10L, 99L, 7L);
    }

    private ProductSellingPointCognitionVersion version(Long id) {
        return ProductSellingPointCognitionVersion.builder()
                .id(id)
                .profileId(10L)
                .versionNumber(1)
                .status("DRAFT")
                .cognitionJson("{\"buyer_cognitions\":[]}")
                .build();
    }
}