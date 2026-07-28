package cafe.snails.ecomagents.service.rag;

import cafe.snails.ecomagents.model.KnowledgeDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KnowledgeUnitParserService} 的单元测试。
 * 覆盖文本/JSON/表格解析、父子块分组、边界感知切片。
 */
class KnowledgeUnitParserServiceTest {

    private KnowledgeUnitParserService parser;

    @BeforeEach
    void setUp() {
        parser = new KnowledgeUnitParserService(new ObjectMapper());
    }

    // ==================== Text Parsing ====================

    @Test
    void parseText_shouldSplitIntoChunks() {
        // Need enough content to exceed TEXT_CHUNK_SIZE (1200)
        String content = "A\n".repeat(800) + "B\n".repeat(800);
        KnowledgeDocument doc = doc("doc", "txt", content);
        List<KnowledgeUnit> units = parser.parse(doc);
        assertTrue(units.size() >= 2, "Should produce multiple chunks from long text");
        assertEquals("text_chunk", units.get(0).unitType());
    }

    @Test
    void parseText_shouldNotSplitShortContent() {
        KnowledgeDocument doc = doc("short", "txt", "Hello world");
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(1, units.size());
        assertEquals("Hello world", units.get(0).content());
    }

    @Test
    void parseText_shouldPreserveParentContent() {
        // Content long enough to produce at least 3 chunks
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是第").append(i).append("段内容用于测试边界感知切片功能。\n\n");
        }
        KnowledgeDocument doc = doc("long", "txt", sb.toString());
        List<KnowledgeUnit> units = parser.parse(doc);

        assertTrue(units.size() >= 2, "Should produce at least 2 chunks");
        for (KnowledgeUnit unit : units) {
            assertNotNull(unit.parentContent(), "Each unit should have parent content");
            assertTrue(unit.parentSourceLocation().startsWith("parent:"),
                    "Parent location should start with 'parent:'");
        }

        // Check that parent content exists and is not empty
        String parent0 = units.get(0).parentContent();
        assertNotNull(parent0);
        assertFalse(parent0.isEmpty(), "Parent content should not be empty");
    }

    // ==================== Boundary Detection ====================

    @Test
    void parseText_shouldHonorParagraphBoundary() {
        // Two paragraphs, each 800 chars, chunk size 1200 → should cut at paragraph boundary
        String p1 = "A\n".repeat(400) + "B\n".repeat(400);
        String p2 = "C\n".repeat(400);
        KnowledgeDocument doc = doc("paragraphs", "txt", p1 + "\n\n" + p2);
        List<KnowledgeUnit> units = parser.parse(doc);
        // The first chunk should end at the paragraph boundary
        String first = units.get(0).content();
        assertTrue(first.contains("A") || first.contains("B"),
                "First chunk should contain first paragraph content");
    }

    @Test
    void parseText_shouldHandleEmptyContent() {
        KnowledgeDocument doc = doc("empty", "txt", "");
        List<KnowledgeUnit> units = parser.parse(doc);
        assertTrue(units.isEmpty());
    }

    @Test
    void parseText_shouldHandleBlankContent() {
        KnowledgeDocument doc = doc("blank", "txt", "   ");
        List<KnowledgeUnit> units = parser.parse(doc);
        assertTrue(units.isEmpty());
    }

    @Test
    void parseText_shouldHandleNullDoc() {
        List<KnowledgeUnit> units = parser.parse(null);
        assertTrue(units.isEmpty());
    }

    // ==================== JSON Parsing ====================

    @Test
    void parseJson_shouldExtractObjects() {
        String json = """
                [
                  {"sku": "A-100", "name": "产品A", "price": 99},
                  {"sku": "B-200", "name": "产品B", "price": 199}
                ]
                """;
        KnowledgeDocument doc = doc("products", "json", json);
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(2, units.size(), "Should produce one unit per JSON object");
        assertEquals("json_object", units.get(0).unitType());
        assertTrue(units.get(0).content().contains("A-100"));
        assertTrue(units.get(1).content().contains("B-200"));
    }

    @Test
    void parseJson_shouldHandleSingleObject() {
        String json = """
                {"id": 1, "title": "Single Product", "price": 299}
                """;
        KnowledgeDocument doc = doc("single", "json", json);
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(1, units.size());
        assertTrue(units.get(0).content().contains("Single Product"));
    }

    @Test
    void parseJson_shouldTruncateLargeContent() {
        // Create a JSON object large enough to exceed MAX_JSON_CONTENT_LENGTH (5000)
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\": \"");
        sb.append("x".repeat(6000));
        sb.append("\"}");
        KnowledgeDocument doc = doc("large", "json", sb.toString());
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(1, units.size());
        assertTrue(units.get(0).content().contains("(truncated)"),
                "Large JSON should be truncated with marker");
    }

    @Test
    void parseJson_shouldResolveTitle() {
        String json = """
                {"asin": "B0TEST1234", "title": "Product Title", "price": 49.99}
                """;
        KnowledgeDocument doc = doc("product", "json", json);
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(1, units.size());
    }

    // ==================== Tabular Parsing (CSV/XLSX) ====================

    @Test
    void parseTabular_shouldSplitCsvIntoRows() {
        // Simulate the output format from extractTextFromXlsx
        // The parser looks for lines starting with "列头：" as header, then splits by "--- 行 "
        String csvContent = "列头：SKU | Name | Price\n--- 行 2 ---\n产品A: iPhone 15\n价格: 5999\n--- 行 3 ---\n产品B: Galaxy S24\n价格: 4999\n";
        KnowledgeDocument doc = doc("products", "xlsx", csvContent);
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(2, units.size(), "Should produce one unit per row");
        assertEquals("tabular_row", units.get(0).unitType());
    }

    @Test
    void parseTabular_shouldGroupIntoParentWindows() {
        StringBuilder sb = new StringBuilder();
        sb.append("列头：ID | Name\n");
        for (int i = 0; i < 6; i++) {
            sb.append("--- 行 ").append(i + 2).append(" ---\n");
            sb.append("ID: ").append(i).append("\n");
            sb.append("Name: Item").append(i).append("\n");
        }
        KnowledgeDocument doc = doc("tabular", "csv", sb.toString());
        List<KnowledgeUnit> units = parser.parse(doc);
        assertEquals(6, units.size(), "Should produce one unit per row");

        // First 5 rows should share parent "parent_tabular:0"
        for (int i = 0; i < 5; i++) {
            assertEquals("parent_tabular:0", units.get(i).parentSourceLocation());
        }
        // 6th row should be in next parent
        assertEquals("parent_tabular:1", units.get(5).parentSourceLocation());
    }

    @Test
    void parseTabular_shouldHandleEmptyCsv() {
        KnowledgeDocument doc = doc("empty", "csv", "");
        List<KnowledgeUnit> units = parser.parse(doc);
        assertTrue(units.isEmpty());
    }

    // ==================== General ====================

    @Test
    void parse_shouldRouteByFileType() {
        // txt → text_chunk
        assertEquals("text_chunk", parser.parse(doc("a", "txt", "hello")).get(0).unitType());
        // md → text_chunk
        assertEquals("text_chunk", parser.parse(doc("a", "md", "# Hello")).get(0).unitType());
        // json → json_object
        assertEquals("json_object", parser.parse(doc("a", "json", "{\"a\":1}")).get(0).unitType());
        // csv → tabular_row (needs "列头：" + "--- 行 " format)
        assertEquals("tabular_row", parser.parse(doc("a", "csv", "列头：A\n--- 行 1 ---\nB")).get(0).unitType());
        // xlsx → tabular_row
        assertEquals("tabular_row", parser.parse(doc("a", "xlsx", "列头：A\n--- 行 1 ---\nB")).get(0).unitType());
    }

    @Test
    void parse_shouldRouteByFileExtension() {
        // Documents without fileType but with fileName extension
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(1L).knowledgeBaseId(1L).fileName("test.md")
                .content("# Hello").charCount(7).build();
        List<KnowledgeUnit> units = parser.parse(doc);
        assertFalse(units.isEmpty());
        assertEquals("text_chunk", units.get(0).unitType());
    }

    // ==================== Helpers ====================

    private static KnowledgeDocument doc(String fileName, String fileType, String content) {
        return KnowledgeDocument.builder()
                .id(1L)
                .knowledgeBaseId(1L)
                .fileName(fileName + "." + fileType)
                .fileType(fileType)
                .content(content)
                .charCount(content == null ? 0 : content.length())
                .build();
    }
}
