package cafe.snails.ecomagents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** AgentScope 配置测试。 */
class AgentScopeConfigTest {

    @Test
    void objectMapper_shouldCreateJacksonMapper() {
        assertInstanceOf(ObjectMapper.class, new AgentScopeConfig().objectMapper());
    }
}
