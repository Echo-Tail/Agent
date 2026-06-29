package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.model.ProductSellingPointCognitionVersion;
import cafe.snails.ecomagents.model.ProductVisualStrategyVersion;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductSellingPointCognitionVersionRepository;
import cafe.snails.ecomagents.repository.ProductVisualStrategyVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisualStrategyServiceTest {

    @Mock private ProductProfileRepository profileRepository;
    @Mock private ProductSellingPointCognitionVersionRepository cognitionVersionRepository;
    @Mock private ProductVisualStrategyVersionRepository visualStrategyVersionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private VisualStrategyService service;

    @BeforeEach
    void setUp() {
        service = new VisualStrategyService(profileRepository, cognitionVersionRepository, visualStrategyVersionRepository, objectMapper);
        lenient().when(visualStrategyVersionRepository.save(any(ProductVisualStrategyVersion.class))).thenAnswer(invocation -> {
            ProductVisualStrategyVersion version = invocation.getArgument(0);
            if (version.getId() == null) version.setId(88L);
            return version;
        });
    }

    @Test
    void generate_shouldCreateDefaultGalleryAndAplusDraftFromConfirmedCognition() throws Exception {
        ProductProfile profile = profile();
        ProductSellingPointCognitionVersion cognition = confirmedCognition();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(cognitionVersionRepository.findById(77L)).thenReturn(Optional.of(cognition));
        when(visualStrategyVersionRepository.countByProfileId(10L)).thenReturn(2);

        ProductVisualStrategyVersion version = service.generate(10L, 77L, null, 7L);

        assertEquals(3, version.getVersionNumber());
        assertEquals("DRAFT", version.getStatus());
        assertEquals("gallery+aplus", version.getContentScope());
        assertEquals(77L, version.getCognitionVersionId());
        assertEquals(22L, version.getProfileVersionId());
        assertEquals(7L, version.getCreatedBy());

        JsonNode strategy = objectMapper.readTree(version.getStrategyJson());
        assertEquals(6, strategy.at("/gallery_strategy/images").size());
        assertEquals(6, strategy.at("/aplus_strategy/modules").size());
        assertTrue(strategy.path("global_constraints").toString().contains("Manual AC"));
        assertTrue(strategy.path("claims_to_avoid").toString().contains("automatic AC"));
        assertTrue(strategy.at("/gallery_strategy/images/0/text_overlays_en/headline").asText().length() > 0);
        assertTrue(strategy.at("/gallery_strategy/images/0/prompt_en").asText().contains("Amazon premium"));
    }

    @Test
    void generate_shouldSupportGalleryOnlyScope() throws Exception {
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(cognitionVersionRepository.findById(77L)).thenReturn(Optional.of(confirmedCognition()));

        ProductVisualStrategyVersion version = service.generate(10L, 77L, List.of("gallery"), 7L);

        JsonNode strategy = objectMapper.readTree(version.getStrategyJson());
        assertEquals("gallery", version.getContentScope());
        assertTrue(strategy.has("gallery_strategy"));
        assertFalse(strategy.has("aplus_strategy"));
    }

    @Test
    void generate_shouldSupportAplusOnlyScope() throws Exception {
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(cognitionVersionRepository.findById(77L)).thenReturn(Optional.of(confirmedCognition()));

        ProductVisualStrategyVersion version = service.generate(10L, 77L, List.of("aplus"), 7L);

        JsonNode strategy = objectMapper.readTree(version.getStrategyJson());
        assertEquals("aplus", version.getContentScope());
        assertFalse(strategy.has("gallery_strategy"));
        assertTrue(strategy.has("aplus_strategy"));
    }

    @Test
    void generate_shouldRejectUnconfirmedCognition() {
        ProductSellingPointCognitionVersion cognition = confirmedCognition();
        cognition.setStatus("DRAFT");
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(cognitionVersionRepository.findById(77L)).thenReturn(Optional.of(cognition));

        assertThrows(BusinessException.class, () -> service.generate(10L, 77L, List.of("gallery"), 7L));
        verify(visualStrategyVersionRepository, never()).save(any());
    }

    @Test
    void generate_shouldUseLatestConfirmedCognitionWhenVersionIdMissing() {
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(cognitionVersionRepository.findTopByProfileIdAndStatusOrderByVersionNumberDesc(10L, "CONFIRMED"))
                .thenReturn(Optional.of(confirmedCognition()));

        ProductVisualStrategyVersion version = service.generate(10L, null, List.of("gallery"), 7L);

        assertEquals(77L, version.getCognitionVersionId());
        verify(cognitionVersionRepository).findTopByProfileIdAndStatusOrderByVersionNumberDesc(10L, "CONFIRMED");
    }

    @Test
    void update_shouldValidateAndSaveDraftJson() {
        ProductVisualStrategyVersion version = draftStrategy();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(visualStrategyVersionRepository.findById(88L)).thenReturn(Optional.of(version));

        ProductVisualStrategyVersion updated = service.update(10L, 88L, minimalStrategyJson(), 7L);

        assertEquals(minimalStrategyJson(), updated.getStrategyJson());
        verify(visualStrategyVersionRepository).save(version);
    }

    @Test
    void update_shouldRejectConfirmedVersion() {
        ProductVisualStrategyVersion version = draftStrategy();
        version.setStatus("CONFIRMED");
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(visualStrategyVersionRepository.findById(88L)).thenReturn(Optional.of(version));

        assertThrows(BusinessException.class, () -> service.update(10L, 88L, minimalStrategyJson(), 7L));
    }

    @Test
    void confirm_shouldMarkVersionConfirmed() {
        ProductVisualStrategyVersion version = draftStrategy();
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(visualStrategyVersionRepository.findById(88L)).thenReturn(Optional.of(version));

        ProductVisualStrategyVersion confirmed = service.confirm(10L, 88L, 7L);

        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(7L, confirmed.getConfirmedBy());
        assertNotNull(confirmed.getConfirmedAt());
        verify(visualStrategyVersionRepository).save(version);
    }

    @Test
    void listVersions_shouldUseRepositoryAfterOwnershipCheck() {
        List<ProductVisualStrategyVersion> versions = List.of(draftStrategy());
        when(profileRepository.findById(10L)).thenReturn(Optional.of(profile()));
        when(visualStrategyVersionRepository.findByProfileIdOrderByVersionNumberDesc(10L)).thenReturn(versions);

        assertSame(versions, service.listVersions(10L, 7L));
    }

    private ProductProfile profile() {
        return ProductProfile.builder()
                .id(10L)
                .userId(7L)
                .productName("Dodge RAM Stereo")
                .category("car stereo")
                .status("CONFIRMED")
                .currentVersionId(22L)
                .build();
    }

    private ProductSellingPointCognitionVersion confirmedCognition() {
        return ProductSellingPointCognitionVersion.builder()
                .id(77L)
                .profileId(10L)
                .profileVersionId(22L)
                .versionNumber(1)
                .status("CONFIRMED")
                .cognitionJson(cognitionJson())
                .build();
    }

    private ProductVisualStrategyVersion draftStrategy() {
        return ProductVisualStrategyVersion.builder()
                .id(88L)
                .profileId(10L)
                .profileVersionId(22L)
                .cognitionVersionId(77L)
                .versionNumber(1)
                .status("DRAFT")
                .contentScope("gallery")
                .strategyJson(minimalStrategyJson())
                .build();
    }

    private String cognitionJson() {
        return """
                {
                  "category": "car_stereo",
                  "category_strategy_version": "car_stereo_v1",
                  "buyer_cognitions": [
                    {"id":"compatibility","enabled":true,"priority":1,"type":"compatibility","visual_model":"infographic","buyer_cognition_cn":"确认车型和 Manual AC 后再购买","buyer_cognition_en":"Confirm vehicle fitment and Manual AC before purchase.","evidence":[{"source_path":"compatibility.vehicle_fitment","source_text":"Dodge RAM 2013-2018 Manual AC only"}]},
                    {"id":"wireless_carplay","enabled":true,"priority":2,"type":"connection","visual_model":"connection","buyer_cognition_cn":"上车连接手机应用","buyer_cognition_en":"Connect phone apps to the dashboard screen.","evidence":[{"source_path":"amazon_listing.bullet_points[1]","source_text":"Wireless CarPlay and Android Auto"}]},
                    {"id":"qled_screen","enabled":true,"priority":3,"type":"display","visual_model":"scenario","buyer_cognition_cn":"大屏导航更清晰","buyer_cognition_en":"A clearer touchscreen makes navigation easier.","evidence":[{"source_path":"amazon_listing.bullet_points[2]","source_text":"9 inch QLED touchscreen"}]},
                    {"id":"backup_camera","enabled":true,"priority":4,"type":"safety","visual_model":"scenario","buyer_cognition_cn":"倒车更安心","buyer_cognition_en":"Rear view support makes parking feel safer.","evidence":[{"source_path":"amazon_listing.bullet_points[3]","source_text":"Supports backup camera input"}]}
                  ],
                  "global_constraints": ["Only show Manual AC compatibility."],
                  "claims_to_avoid": ["Do not claim automatic AC compatibility."]
                }
                """;
    }

    private String minimalStrategyJson() {
        return """
                {
                  "category": "car_stereo",
                  "content_scope": ["gallery"],
                  "gallery_strategy": {"images": []}
                }
                """;
    }
}