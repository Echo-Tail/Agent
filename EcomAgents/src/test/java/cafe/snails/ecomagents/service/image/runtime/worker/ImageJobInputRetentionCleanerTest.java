package cafe.snails.ecomagents.service.image.runtime.worker;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ImageJobInputRetentionCleanerTest {
    @TempDir Path tempDir;

    @Test
    void shouldResolveOnlyPathsInsideImageJobStorage() {
        var cleaner = new ImageJobInputRetentionCleaner(mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(cleaner, "uploadDir", tempDir.toString());
        assertTrue(cleaner.resolve("/uploads/image-jobs/12/inputs/a.png")
                .startsWith(tempDir.resolve("image-jobs").toAbsolutePath()));
        assertThrows(IllegalArgumentException.class,
                () -> cleaner.resolve("/uploads/image-jobs/../../database.properties"));
        assertThrows(IllegalArgumentException.class,
                () -> cleaner.resolve("/uploads/generate/a.png"));
    }
}
