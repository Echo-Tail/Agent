package cafe.snails.ecomagents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

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

    /** 构建 AgentScope Model 实例，基于全局 {@link LlmConfig} */
    @Bean
    public Model agentscopeModel(LlmConfig llmConfig) {
        return OpenAIChatModel.builder()
                .apiKey(llmConfig.getApiKey())
                .modelName(llmConfig.getModel())
                .baseUrl(extractBaseUrl(llmConfig.getApiUrl()))
                .endpointPath(extractPath(llmConfig.getApiUrl()))
                .build();
    }

    /** 从完整 URL 中提取 base URL（scheme://host:port） */
    static String extractBaseUrl(String apiUrl) {
        try {
            URI uri = URI.create(apiUrl);
            int port = uri.getPort();
            return port > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + port
                    : uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return "https://api.openai.com";
        }
    }

    /** 从完整 URL 中提取路径部分（/path?query） */
    static String extractPath(String apiUrl) {
        try {
            URI uri = URI.create(apiUrl);
            String path = uri.getPath();
            String query = uri.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            return "/v1/chat/completions";
        }
    }
}
