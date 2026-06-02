package cafe.snails.ecomagents.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigTest {

    @Test
    void defaults_shouldExposeExpectedValues() {
        var config = new LlmConfig();

        assertEquals("https://api.openai.com/v1/chat/completions", config.getApiUrl());
        assertEquals("sk-placeholder", config.getApiKey());
        assertEquals("gpt-4o-mini", config.getModel());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature());
        assertEquals(60, config.getStreamTimeout());
        assertEquals(30, config.getConnectionTimeout());
        assertEquals(55, config.getReadTimeout());
        assertEquals("ollama", config.getEmbeddingProvider());
        assertEquals("bge-m3:latest", config.getEmbeddingModel());
        assertEquals(1024, config.getEmbeddingDimension());
        assertEquals(5, config.getRagSearchLimit());
        assertEquals(0.15, config.getRagSimilarityThreshold());
        assertEquals(8, config.getRagRetrievalTimeout());
        assertEquals(16000, config.getRagMaxContextChars());
    }

    @Test
    void setters_shouldUpdateAllValues() {
        var config = new LlmConfig();

        config.setApiUrl("https://example.com/chat");
        config.setApiKey("key");
        config.setModel("model");
        config.setMaxTokens(123);
        config.setTemperature(0.2);
        config.setStreamTimeout(10);
        config.setConnectionTimeout(11);
        config.setReadTimeout(12);
        config.setEmbeddingProvider("openai");
        config.setEmbeddingApiUrl("https://example.com/embeddings");
        config.setEmbeddingApiKey("emb-key");
        config.setEmbeddingModel("embed");
        config.setEmbeddingDimension(768);
        config.setRagSearchLimit(9);
        config.setRagSimilarityThreshold(0.4);
        config.setRagRetrievalTimeout(6);
        config.setRagMaxContextChars(2048);

        assertEquals("https://example.com/chat", config.getApiUrl());
        assertEquals("key", config.getApiKey());
        assertEquals("model", config.getModel());
        assertEquals(123, config.getMaxTokens());
        assertEquals(0.2, config.getTemperature());
        assertEquals(10, config.getStreamTimeout());
        assertEquals(11, config.getConnectionTimeout());
        assertEquals(12, config.getReadTimeout());
        assertEquals("openai", config.getEmbeddingProvider());
        assertEquals("https://example.com/embeddings", config.getEmbeddingApiUrl());
        assertEquals("emb-key", config.getEmbeddingApiKey());
        assertEquals("embed", config.getEmbeddingModel());
        assertEquals(768, config.getEmbeddingDimension());
        assertEquals(9, config.getRagSearchLimit());
        assertEquals(0.4, config.getRagSimilarityThreshold());
        assertEquals(6, config.getRagRetrievalTimeout());
        assertEquals(2048, config.getRagMaxContextChars());
    }
}
