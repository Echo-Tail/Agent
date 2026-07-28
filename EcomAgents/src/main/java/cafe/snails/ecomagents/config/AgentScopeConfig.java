package cafe.snails.ecomagents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope SDK 配置，注入 Model 和兼容的 ObjectMapper 实例。
 * <p>AgentScope 使用 Jackson 2.x 而 Spring Boot 4.x 自动配置 Jackson 3.x，
 * 此处显式提供 Jackson 2 的 ObjectMapper 以确保兼容。</p>
 */
@Configuration
public class AgentScopeConfig {

    /**
     * 提供 AgentScope SDK 兼容的 Jackson ObjectMapper。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
