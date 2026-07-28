package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductSellingPointCognitionVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellingPointCognitionServiceTest {

    @Mock private ProductProfileRepository profileRepository;
    @Mock private ProductSellingPointCognitionVersionRepository cognitionVersionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SellingPointCognitionService service;

    @BeforeEach
    void setUp() {
        service = new SellingPointCognitionService(profileRepository, cognitionVersionRepository, objectMapper);
        lenient().when(cognitionVersionRepository.save(any(ProductSellingPointCognitionVersion.class))).thenAnswer(invocation -> {
            ProductSellingPointCognitionVersion version = invocation.getArgument(0);
            if (version.getId() == null) version.setId(99L);
            return version;
        });
    }

    @Test
    void generate_shouldCreateDraftVersionWithEvidenceAndConstraints() throws Exception {
        ProductProfile profile = profileWithFacts();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.countByProfileId(10L)).thenReturn(1);

        ProductSellingPointCognitionVersion version = service.generate(10L, 7L);

        assertEquals(2, version.getVersionNumber());
        assertEquals("DRAFT", version.getStatus());
        assertEquals(10L, version.getProfileId());
        assertEquals(22L, version.getProfileVersionId());
        assertEquals(7L, version.getCreatedBy());
        assertNotNull(version.getSourceFactsHash());
        assertEquals(64, version.getSourceFactsHash().length());

        JsonNode json = objectMapper.readTree(version.getCognitionJson());
        assertEquals("car_stereo", json.path("category").asText());
        assertTrue(json.path("buyer_cognitions").size() >= 4);
        assertTrue(json.path("buyer_cognitions").size() <= 20);
        assertTrue(json.path("buyer_cognitions").get(0).path("evidence").size() > 0);
        assertTrue(json.path("global_constraints").toString().contains("Dodge RAM"));
        assertTrue(json.path("claims_to_avoid").toString().contains("automatic AC"));
    }

    @Test
    void generate_shouldRejectProfileWithoutFacts() {
        ProductProfile profile = ProductProfile.builder()
                .id(10L)
                .userId(7L)
                .productName("No facts")
                .category("car stereo")
                .status("PENDING_CONFIRM")
                .build();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));

        assertThrows(BusinessException.class, () -> service.generate(10L, 7L));
        verify(cognitionVersionRepository, never()).save(any());
    }

    @Test
    void generate_shouldRejectOtherUsersProfile() {
        ProductProfile profile = profileWithFacts();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));

        assertThrows(BusinessException.class, () -> service.generate(10L, 8L));
        verify(cognitionVersionRepository, never()).save(any());
    }

    @Test
    void update_shouldValidateAndSaveDraftJson() {
        ProductProfile profile = profileWithFacts();
        ProductSellingPointCognitionVersion version = ProductSellingPointCognitionVersion.builder()
                .id(99L)
                .profileId(10L)
                .versionNumber(1)
                .status("DRAFT")
                .cognitionJson(minimalCognitionJson())
                .createdBy(7L)
                .build();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.findById(99L)).thenReturn(Optional.of(version));

        ProductSellingPointCognitionVersion updated = service.update(10L, 99L, minimalCognitionJson(), 7L);

        assertEquals(minimalCognitionJson(), updated.getCognitionJson());
        verify(cognitionVersionRepository).save(version);
    }

    @Test
    void update_shouldRejectConfirmedVersion() {
        ProductProfile profile = profileWithFacts();
        ProductSellingPointCognitionVersion version = ProductSellingPointCognitionVersion.builder()
                .id(99L)
                .profileId(10L)
                .versionNumber(1)
                .status("CONFIRMED")
                .cognitionJson(minimalCognitionJson())
                .build();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.findById(99L)).thenReturn(Optional.of(version));

        assertThrows(BusinessException.class, () -> service.update(10L, 99L, minimalCognitionJson(), 7L));
    }

    @Test
    void confirm_shouldMarkVersionConfirmed() {
        ProductProfile profile = profileWithFacts();
        ProductSellingPointCognitionVersion version = ProductSellingPointCognitionVersion.builder()
                .id(99L)
                .profileId(10L)
                .versionNumber(1)
                .status("DRAFT")
                .cognitionJson(minimalCognitionJson())
                .build();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.findById(99L)).thenReturn(Optional.of(version));

        ProductSellingPointCognitionVersion confirmed = service.confirm(10L, 99L, 7L);

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(7L, confirmed.getConfirmedBy());
        assertNotNull(confirmed.getConfirmedAt());
        verify(cognitionVersionRepository).save(version);
    }

    @Test
    void listVersions_shouldUseRepositoryAfterOwnershipCheck() {
        ProductProfile profile = profileWithFacts();
        List<ProductSellingPointCognitionVersion> versions = new ArrayList<>();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.findByProfileIdOrderByVersionNumberDesc(10L)).thenReturn(versions);

        assertSame(versions, service.listVersions(10L, 7L));
    }

    private ProductProfile profileWithFacts() {
        return ProductProfile.builder()
                .id(10L)
                .userId(7L)
                .productName("Dodge RAM Stereo")
                .brand("Kissound")
                .sku("B0TEST1234")
                .category("car stereo")
                .status("PENDING_CONFIRM")
                .currentVersionId(22L)
                .productFactsJson(productFactsJson())
                .build();
    }

    private String productFactsJson() {
        return """
                {
                  "identity": {"product_name": "Dodge RAM Stereo", "brand": "Kissound"},
                  "amazon_listing": {
                    "title": "Car Stereo for Dodge RAM 1500 2500 3500 2013-2018",
                    "bullet_points": [
                      "Only fit Dodge RAM 1500 / 2500 / 3500 2013-2018 Manual AC.",
                      "Wireless CarPlay and Android Auto with Bluetooth and Wi-Fi.",
                      "9 inch QLED touchscreen with 1360x800 resolution.",
                      "Supports backup camera input for safer parking."
                    ],
                    "product_details": {
                      "Connectivity Technology": "2 USB, Bluetooth 5.4, WiFi-6",
                      "Warranty Description": "1 year"
                    }
                  },
                  "compatibility": {
                    "vehicle_fitment": ["Dodge RAM 1500 / 2500 / 3500 2013-2018 Manual AC only"],
                    "fitment_notes": "Please confirm your center console before purchase."
                  }
                }
                """;
    }

    private String minimalCognitionJson() {
        return """
                {
                  "category": "car_stereo",
                  "category_strategy_version": "car_stereo_v1",
                  "status": "draft",
                  "buyer_cognitions": [
                    {
                      "id": "wireless_carplay",
                      "enabled": true,
                      "priority": 2,
                      "type": "connection",
                      "visual_model": "connection",
                      "feature": "Wireless CarPlay",
                      "buyer_cognition_en": "Connect phone apps to the dashboard.",
                      "evidence": [{"source_path": "amazon_listing.bullet_points[1]", "source_text": "Wireless CarPlay"}]
                    }
                  ]
                }
                """;
    }
}
