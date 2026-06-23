package cafe.snails.ecomagents.controller;

import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.BrightDataScrapeRequest;
import cafe.snails.ecomagents.dto.BrightDataScrapeResponse;
import cafe.snails.ecomagents.security.CurrentUserId;
import cafe.snails.ecomagents.service.BrightDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/bright-data")
@RequiredArgsConstructor
public class BrightDataController {

    private final BrightDataService brightDataService;

    @PostMapping("/scrape")
    public ApiResponse<BrightDataScrapeResponse> scrape(
            @RequestBody BrightDataScrapeRequest request,
            @CurrentUserId Long userId) {
        return brightDataService.scrape(request, userId);
    }

    @PostMapping("/scrape-asin")
    public ApiResponse<BrightDataScrapeResponse> scrapeAsin(
            @RequestParam String asin,
            @CurrentUserId Long userId) {
        String cleanAsin = asin.trim().toUpperCase();
        // Check cache first to avoid duplicate API calls
        BrightDataScrapeResponse cached = brightDataService.findRecentByAsin(cleanAsin, userId);
        if (cached != null) {
            return ApiResponse.success(cached);
        }
        BrightDataScrapeRequest req = new BrightDataScrapeRequest();
        req.setInput(List.of(Map.of("url", "https://www.amazon.com/dp/" + cleanAsin)));
        return brightDataService.scrape(req, userId);
    }
}
