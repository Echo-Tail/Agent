package cafe.snails.ecomagents.service.image.runtime;

import cafe.snails.ecomagents.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageSizePolicyTest {
    @Test void qwen20UsesModelDefaultAndAcceptsRecommendedLandscape() {
        assertEquals("2048*2048", ImageSizePolicy.resolve("qwen-image-2.0-pro", null, "1024x1024"));
        assertEquals("2688*1536", ImageSizePolicy.resolve("qwen-image-2.0-pro", "2688x1536", "1024x1024"));
    }

    @Test void qwen20RejectsTotalPixelsOutsideRange() {
        assertThrows(BusinessException.class,
                () -> ImageSizePolicy.resolve("qwen-image-2.0-pro", "4096x4096", "1024x1024"));
    }

    @Test void qwenMaxAndPlusUseEnumeratedSizes() {
        assertEquals("1664*928", ImageSizePolicy.resolve("qwen-image-max", null, "1024x1024"));
        assertEquals("928*1664", ImageSizePolicy.resolve("qwen-image-plus-2025", "928x1664", "1024x1024"));
        assertThrows(BusinessException.class,
                () -> ImageSizePolicy.resolve("qwen-image-max", "1024x1024", "1024x1024"));
    }
}
