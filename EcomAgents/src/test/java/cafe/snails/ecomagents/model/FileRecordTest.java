package cafe.snails.ecomagents.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileRecord} 实体单元测试。
 * <p>验证 Builder / getter / setter / transient 方法。</p>
 */
class FileRecordTest {

    @Test
    void builder_shouldSetAllFields() {
        var now = LocalDateTime.now();
        var record = FileRecord.builder()
                .id(1L)
                .originalName("test.txt")
                .storedPath("/uploads/test.txt")
                .fileSize(100L)
                .mimeType("text/plain")
                .uploadedAt(now)
                .uploadedBy(10L)
                .contextType("PRIVATE")
                .contextId(5L)
                .build();

        assertEquals(1L, record.getId());
        assertEquals("test.txt", record.getOriginalName());
        assertEquals(100L, record.getFileSize());
        assertEquals("text/plain", record.getMimeType());
        assertEquals(now, record.getUploadedAt());
        assertEquals(10L, record.getUploadedBy());
        assertEquals("PRIVATE", record.getContextType());
        assertEquals(5L, record.getContextId());
    }

    @Test
    void builder_shouldAllowNullContext() {
        var record = FileRecord.builder()
                .id(2L)
                .originalName("no-context.txt")
                .storedPath("/uploads/no-context.txt")
                .fileSize(50L)
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(1L)
                .build();

        assertNull(record.getContextType());
        assertNull(record.getContextId());
    }

    @Test
    void getUrl_shouldReturnDownloadPath() {
        var record = FileRecord.builder().id(42L).build();
        assertEquals("/v1/files/42/download", record.getUrl());
    }

    @Test
    void setters_shouldUpdateFields() {
        var record = new FileRecord();
        record.setId(10L);
        record.setOriginalName("updated.txt");
        record.setContextType("AGENT");
        record.setContextId(100L);

        assertEquals(10L, record.getId());
        assertEquals("updated.txt", record.getOriginalName());
        assertEquals("AGENT", record.getContextType());
        assertEquals(100L, record.getContextId());
    }
}
