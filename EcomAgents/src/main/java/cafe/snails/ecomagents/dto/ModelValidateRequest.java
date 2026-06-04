package cafe.snails.ecomagents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置验证请求 DTO，测试 LLM API 连通性并获取可用模型列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelValidateRequest {
    /** LLM 服务基础地址。 */
    private String baseUrl;
    /** 模型供应商标识。 */
    private String provider;
    /** API 协议类型，例如 openai、azure。 */
    private String apiType;
    /** API 版本，主要用于 Azure OpenAI 等版本化接口。 */
    private String apiVersion;
    /** 用于验证连通性的 API Key。 */
    private String apiKey;
}
