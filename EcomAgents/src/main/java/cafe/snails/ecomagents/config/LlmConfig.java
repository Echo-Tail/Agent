package cafe.snails.ecomagents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 全局配置，绑定 application.properties 中 llm.* 前缀的配置项。
 * <p>可通过环境变量 {@code LLM_API_KEY} 或配置文件中的 {@code llm.api.key} 设置。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /** API 请求地址 */
    private String apiUrl = "https://api.openai.com/v1/chat/completions";
    /** API 密钥（占位符 sk-placeholder 表示未配置） */
    private String apiKey = "sk-placeholder";
    /** 模型名称 */
    private String model = "gpt-4o-mini";
    /** 最大输出 token 数 */
    private int maxTokens = 2048;
    /** 生成温度 */
    private double temperature = 0.7;
    /** LLM 流式调用超时时间（秒） */
    private long streamTimeout = 60;
    /** 模型 API 连接超时时间（秒） */
    private long connectionTimeout = 30;
    /** 模型 API 读取超时时间（秒） */
    private long readTimeout = 55;
    private String embeddingProvider = "ollama";
    private String embeddingApiUrl;
    private String embeddingApiKey;
    private String embeddingModel = "bge-m3:latest";
    private int embeddingDimension = 1024;
    private int ragSearchLimit = 5;
    private double ragSimilarityThreshold = 0.15;
    private long ragRetrievalTimeout = 8;
    private int ragMaxContextChars = 16000;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public long getStreamTimeout() { return streamTimeout; }
    public void setStreamTimeout(long streamTimeout) { this.streamTimeout = streamTimeout; }
    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    public long getReadTimeout() { return readTimeout; }
    public void setReadTimeout(long readTimeout) { this.readTimeout = readTimeout; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String embeddingProvider) { this.embeddingProvider = embeddingProvider; }
    public String getEmbeddingApiUrl() { return embeddingApiUrl; }
    public void setEmbeddingApiUrl(String embeddingApiUrl) { this.embeddingApiUrl = embeddingApiUrl; }
    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String embeddingApiKey) { this.embeddingApiKey = embeddingApiKey; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public int getRagSearchLimit() { return ragSearchLimit; }
    public void setRagSearchLimit(int ragSearchLimit) { this.ragSearchLimit = ragSearchLimit; }
    public double getRagSimilarityThreshold() { return ragSimilarityThreshold; }
    public void setRagSimilarityThreshold(double ragSimilarityThreshold) { this.ragSimilarityThreshold = ragSimilarityThreshold; }
    public long getRagRetrievalTimeout() { return ragRetrievalTimeout; }
    public void setRagRetrievalTimeout(long ragRetrievalTimeout) { this.ragRetrievalTimeout = ragRetrievalTimeout; }
    public int getRagMaxContextChars() { return ragMaxContextChars; }
    public void setRagMaxContextChars(int ragMaxContextChars) { this.ragMaxContextChars = ragMaxContextChars; }
}
