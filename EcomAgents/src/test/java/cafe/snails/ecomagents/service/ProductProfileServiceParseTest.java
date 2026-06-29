package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.BrightDataScrapeResponse;
import cafe.snails.ecomagents.exception.BusinessException;
import cafe.snails.ecomagents.model.ProductProfile;
import cafe.snails.ecomagents.repository.AiModelRepository;
import cafe.snails.ecomagents.repository.ProductProfileImageRepository;
import cafe.snails.ecomagents.repository.ProductProfileRepository;
import cafe.snails.ecomagents.repository.ProductProfileVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductProfileServiceParseTest {

    @Mock private ProductProfileRepository profileRepository;
    @Mock private ProductProfileVersionRepository versionRepository;
    @Mock private ProductProfileImageRepository imageRepository;
    @Mock private AiModelRepository aiModelRepository;
    @Mock private BrightDataService brightDataService;
    @Mock private WebClient.Builder webClientBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProductProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProductProfileService(
                profileRepository, versionRepository, imageRepository,
                aiModelRepository, brightDataService, webClientBuilder, objectMapper);
        lenient().when(aiModelRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
        lenient().when(aiModelRepository.findByModelTypeAndEnabled("TEXT", true)).thenReturn(List.of());
        lenient().when(profileRepository.existsByProductName(any())).thenReturn(false);
        lenient().when(profileRepository.save(any(ProductProfile.class))).thenAnswer(invocation -> {
            ProductProfile profile = invocation.getArgument(0);
            if (profile.getId() == null) profile.setId(100L);
            return profile;
        });
    }

    @Test
    void createFromAsin_shouldPersistRawSourceAndExtractListingFactsWithoutTextModel() throws Exception {
        String asin = "B0TEST1234";
        when(profileRepository.findBySku(asin)).thenReturn(Optional.empty());
        when(brightDataService.scrape(any(), eq(7L))).thenReturn(ApiResponse.success(BrightDataScrapeResponse.builder()
                .records(List.of(sampleRecord(asin, 8)))
                .recordId(55L)
                .message("success")
                .build()));

        ProductProfile profile = service.createFromAsin(asin, 7L);

        assertEquals("BRIGHT_DATA_ASIN", profile.getSourceType());
        assertEquals(asin, profile.getSourceAsin());
        assertNotNull(profile.getSourceRawJson());
        assertEquals("ExampleBrand", profile.getBrand());
        assertEquals("F150-101", profile.getModelNumber());
        assertEquals(asin, profile.getSku());
        assertEquals(asin, profile.getTargetAsin());
        assertEquals("PENDING_CONFIRM", profile.getStatus());
        assertTrue(profile.getProductName().contains("Ford F150"));

        JsonNode facts = objectMapper.readTree(profile.getProductFactsJson());
        assertEquals("Android 13 Car Stereo for Ford F150 2009-2014", facts.at("/amazon_listing/title").asText());
        assertEquals(8, facts.at("/amazon_listing/bullet_points").size(), "bullet_points must not be hard-coded to 5");
        assertEquals("10.1 Inches", facts.at("/amazon_listing/product_details/Screen Size").asText());
        assertEquals("Bluetooth, Wi-Fi, USB", facts.at("/amazon_listing/product_details/Connectivity Technology").asText());
        assertTrue(facts.at("/features/carplay").asText().contains("CarPlay"));
        assertTrue(facts.at("/compatibility/vehicle_fitment").size() > 0);
        assertTrue(facts.has("review"));
    }

    @Test
    void reparse_shouldUseStoredBrightDataRawJsonForAsinProfiles() throws Exception {
        String asin = "B0TEST1234";
        String raw = objectMapper.writeValueAsString(Map.of("records", List.of(sampleRecord(asin, 6))));
        ProductProfile existing = ProductProfile.builder()
                .id(11L)
                .userId(7L)
                .productName("ASIN-" + asin)
                .sku(asin)
                .category("car stereo")
                .status("PARSE_FAILED")
                .sourceType("BRIGHT_DATA_ASIN")
                .sourceAsin(asin)
                .sourceRawJson(raw)
                .build();
        when(profileRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(profileRepository.findBySku(asin)).thenReturn(Optional.empty());

        ProductProfile reparsed = service.reparse(11L, 7L);

        assertEquals("PENDING_CONFIRM", reparsed.getStatus());
        JsonNode facts = objectMapper.readTree(reparsed.getProductFactsJson());
        assertEquals(6, facts.at("/amazon_listing/bullet_points").size());
        assertEquals("ExampleBrand", reparsed.getBrand());
        verify(brightDataService, never()).scrape(any(), any());
    }

    @Test
    void createFromAsin_shouldExtractBrightDataArrayProductDetailsAndFeatures() throws Exception {
        String asin = "B0FY2ZRS14";
        when(profileRepository.findBySku(asin)).thenReturn(Optional.empty());
        when(brightDataService.scrape(any(), eq(7L))).thenReturn(ApiResponse.success(BrightDataScrapeResponse.builder()
                .records(List.of(brightDataArrayDetailsRecord(asin)))
                .recordId(56L)
                .message("success")
                .build()));

        ProductProfile profile = service.createFromAsin(asin, 7L);

        JsonNode facts = objectMapper.readTree(profile.getProductFactsJson());
        assertEquals(2, facts.at("/amazon_listing/bullet_points").size(), "Bright Data features must be preserved as bullet_points");
        assertEquals("2 USB, Bluetooth 5.4, WiFi-6", facts.at("/amazon_listing/product_details/Connectivity Technology").asText());
        assertEquals("1 year", facts.at("/amazon_listing/product_details/Warranty Description").asText());
        assertEquals("1 year", facts.path("warranty").asText());
        assertTrue(facts.at("/included_items/0").asText().contains("AHD 1080P Backup Camera"));
        assertTrue(facts.at("/compatibility/vehicle_fitment").toString().contains("dodge ram"));
    }

    @Test
    void createFromAsin_shouldRejectDuplicateSkuEvenWhenProductNameChanged() {
        String asin = "B0TEST1234";
        when(profileRepository.findBySku(asin)).thenReturn(Optional.of(ProductProfile.builder()
                .id(1L)
                .userId(7L)
                .productName("Real Product Title")
                .sku(asin)
                .category("car stereo")
                .status("CONFIRMED")
                .build()));

        assertThrows(BusinessException.class, () -> service.createFromAsin(asin, 7L));
        verify(brightDataService, never()).scrape(any(), any());
    }

    private Map<String, Object> brightDataArrayDetailsRecord(String asin) {
        return Map.of(
                "asin", asin,
                "title", "Car Stereo for Dodge RAM 1500 2500 3500 2013-2018 with Wireless CarPlay and Android Auto",
                "brand", "Kissound",
                "manufacturer", "Kissound",
                "description", "Custom-designed for Dodge RAM 1500 / 2500 / 3500 / 2013-2018 Manual AC only.",
                "features", List.of(
                        "Applicable Models: custom-designed for Dodge RAM 1500 / 2500 / 3500 / 2013-2018 Manual AC only.",
                        "Wireless CarPlay and Android Auto with Bluetooth, Wi-Fi and GPS navigation."),
                "product_details", List.of(
                        Map.of("type", "Connectivity Technology", "value", "2 USB, Bluetooth 5.4, WiFi-6"),
                        Map.of("type", "Warranty Description", "value", "1 year"),
                        Map.of("type", "Built-In Media", "value", "AHD 1080P Backup Camera with 19.7ft Cable, GPS Antenna, Head Unit, Two USB Cables, User Manual"))
        );
    }

    private Map<String, Object> sampleRecord(String asin, int bulletCount) {
        List<String> bullets = new ArrayList<>();
        bullets.add("Compatible with Ford F-150 2009 2010 2011 2012 2013 2014 standard radio dashboard.");
        bullets.add("10.1 inch HD touchscreen with wireless Apple CarPlay and Android Auto.");
        bullets.add("Built-in Bluetooth, WiFi, GPS navigation, FM radio and backup camera input.");
        bullets.add("Supports steering wheel controls and original vehicle functions.");
        bullets.add("Package includes radio unit, wiring harness, GPS antenna and user manual.");
        for (int i = 6; i <= bulletCount; i++) bullets.add("Additional product bullet " + i + " with useful listing detail.");
        return Map.of(
                "asin", asin,
                "title", "Android 13 Car Stereo for Ford F150 2009-2014",
                "brand", "ExampleBrand",
                "model", "F150-101",
                "description", "Upgrade your factory radio with a modern Android car stereo for Ford F150 2009-2014.",
                "bullet_points", bullets,
                "product_details", Map.of(
                        "Brand", "ExampleBrand",
                        "Model", "F150-101",
                        "Screen Size", "10.1 Inches",
                        "Connectivity Technology", "Bluetooth, Wi-Fi, USB",
                        "Compatible Devices", "Ford F-150 2009-2014"),
                "included_components", List.of("radio unit", "wiring harness", "GPS antenna", "user manual")
        );
    }
}
