package cafe.snails.ecomagents.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 模型调用的运行时参数。这些参数描述调用策略，而不是某个具体模型。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "model.runtime")
public class ModelRuntimeProperties {
    private long streamTimeout = 60;
    private long connectionTimeout = 30;
    private long readTimeout = 55;
}
