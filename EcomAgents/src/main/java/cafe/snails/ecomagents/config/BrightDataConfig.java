package cafe.snails.ecomagents.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bright Data API 配置。
 * <p>优先读环境变量 BRIGHT_API_KEY，其次读 application.properties 中的 brightdata.api-key。</p>
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "brightdata")
public class BrightDataConfig {

    /** Bright Data API Key，从账号设置获取：https://brightdata.com/cp/setting/users */
    private String apiKey = "";

    /** API 基础地址，默认 https://api.brightdata.com */
    private String baseUrl = "https://api.brightdata.com";

    /** 默认数据集 ID（可选），可在请求中覆盖 */
    private String defaultDatasetId = "";

    @PostConstruct
    public void init() {
        // Environment variable takes precedence over config file
        String envKey = System.getenv("BRIGHT_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            this.apiKey = envKey.trim();
            log.info("Bright Data API Key loaded from environment variable BRIGHT_API_KEY");
        } else {
            log.warn("Bright Data API Key not set via BRIGHT_API_KEY env var, using config file value");
        }
    }
}
