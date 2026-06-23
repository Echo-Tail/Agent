package cafe.snails.ecomagents.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Bright Data API 配置。
 * <p>从 application.properties 读取 brightdata.* 前缀的属性。</p>
 */
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
}
