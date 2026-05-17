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
    private String baseUrl;
    private String apiType;
    private String apiVersion;
    private String apiKey;
}
