package cafe.snails.ecomagents.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识检索策略参数，与具体的聊天或向量模型解耦。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private int searchLimit = 5;
    private double similarityThreshold = 0.15;
    private long retrievalTimeout = 8;
    private int maxContextChars = 16000;
}
