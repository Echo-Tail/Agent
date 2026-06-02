package cafe.snails.ecomagents.tool;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    private final WebSearchTool tool = new WebSearchTool("test-key");

    @Test
    void search_shouldRejectBlankQueriesBeforeNetworkCall() {
        assertTrue(tool.search(null, 3).contains("不能为空"));
        assertTrue(tool.search("   ", 3).contains("不能为空"));
    }

    @Test
    void formatResults_shouldIncludeAnswerAndResults() throws Exception {
        String json = """
                {
                  "answer": "short answer",
                  "results": [
                    {"title": "Title", "url": "https://example.com", "content": "content"}
                  ]
                }
                """;

        String result = invokeFormatResults("query", json);

        assertTrue(result.contains("query"));
        assertTrue(result.contains("short answer"));
        assertTrue(result.contains("Title"));
        assertTrue(result.contains("https://example.com"));
        assertTrue(result.contains("content"));
    }

    @Test
    void formatResults_shouldHandleEmptyResults() throws Exception {
        String result = invokeFormatResults("query", "{\"answer\":\"\",\"results\":[]}");

        assertTrue(result.contains("query"));
        assertTrue(result.contains("未找到") || result.contains("鏈壘"));
    }

    @Test
    void formatResults_shouldUseDefaultFieldsAndTruncateLongContent() throws Exception {
        String longContent = "a".repeat(900);
        String json = """
                {"results":[{"content":"%s"}]}
                """.formatted(longContent);

        String result = invokeFormatResults("query", json);

        assertTrue(result.contains("..."));
        assertTrue(result.length() < longContent.length() + 300);
    }

    @Test
    void formatResults_shouldReportJsonParseErrors() throws Exception {
        String result = invokeFormatResults("query", "not json");

        assertTrue(result.contains("解析") || result.contains("瑙ｆ瀽"));
    }

    @Test
    void truncate_shouldKeepShortValuesAndShortenLongValues() throws Exception {
        assertEquals("abc", invokeTruncate("abc", 10));
        assertEquals("ab...", invokeTruncate("abcdef", 5));
        assertNull(invokeTruncate(null, 5));
    }

    private String invokeFormatResults(String query, String responseJson) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("formatResults", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(tool, query, responseJson);
    }

    private String invokeTruncate(String value, int maxChars) throws Exception {
        Method method = WebSearchTool.class.getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(tool, value, maxChars);
    }
}
