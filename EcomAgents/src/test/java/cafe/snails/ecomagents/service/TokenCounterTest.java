package cafe.snails.ecomagents.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TokenCounter} 单元测试。
 */
class TokenCounterTest {

    private final TokenCounter counter = new TokenCounter();

    @Test
    void count_shouldReturnPositiveForNonEmptyText() {
        int count = counter.count("gpt-4", "Hello world");
        assertTrue(count > 0, "Token count should be positive for non-empty text");
    }

    @Test
    void count_shouldReturnZeroForEmptyText() {
        assertEquals(0, counter.count("gpt-4", ""));
    }

    @Test
    void count_shouldReturnZeroForNullText() {
        assertEquals(0, counter.count("gpt-4", null));
    }

    @Test
    void count_shouldHandleDifferentModelNames() {
        int count1 = counter.count("gpt-4", "Hello world test");
        int count2 = counter.count("gpt-3.5-turbo", "Hello world test");
        assertTrue(count1 > 0);
        assertTrue(count2 > 0);
    }
}
