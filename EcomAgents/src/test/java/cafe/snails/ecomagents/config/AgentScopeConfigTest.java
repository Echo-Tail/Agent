package cafe.snails.ecomagents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentScopeConfigTest {

    @Test
    void objectMapper_shouldCreateJacksonMapper() {
        assertInstanceOf(ObjectMapper.class, new AgentScopeConfig().objectMapper());
    }

    @Test
    void extractBaseUrl_shouldHandlePortAndInvalidUrls() {
        assertEquals("https://api.example.com:8443",
                AgentScopeConfig.extractBaseUrl("https://api.example.com:8443/v1/chat?x=1"));
        assertEquals("https://api.example.com",
                AgentScopeConfig.extractBaseUrl("https://api.example.com/v1/chat"));
        assertEquals("https://api.openai.com", AgentScopeConfig.extractBaseUrl("not a url"));
    }

    @Test
    void extractPath_shouldIncludeQueryAndFallbackForInvalidUrls() {
        assertEquals("/v1/chat?x=1", AgentScopeConfig.extractPath("https://api.example.com/v1/chat?x=1"));
        assertEquals("/v1/chat", AgentScopeConfig.extractPath("https://api.example.com/v1/chat"));
        assertEquals("/v1/chat/completions", AgentScopeConfig.extractPath("not a url"));
    }
}
