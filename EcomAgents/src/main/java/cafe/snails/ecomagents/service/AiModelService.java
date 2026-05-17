package cafe.snails.ecomagents.service;

import cafe.snails.ecomagents.config.LlmConfig;
import cafe.snails.ecomagents.dto.ApiResponse;
import cafe.snails.ecomagents.dto.ModelValidateRequest;
import cafe.snails.ecomagents.model.AiModel;
import cafe.snails.ecomagents.repository.AiModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 模型配置业务逻辑，包括模型的 CRUD、默认模型管理、以及 AgentScope GenerateOptions 的构建。
 */
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository repository;
    private final LlmConfig llmConfig;

    /** 获取所有模型配置 */
    public ApiResponse<List<AiModel>> listModels() {
        return ApiResponse.success(repository.findAll());
    }

    /** 获取已启用的模型列表 */
    public ApiResponse<List<AiModel>> listEnabledModels() {
        return ApiResponse.success(repository.findAll().stream()
                .filter(AiModel::getEnabled).toList());
    }

    /** 根据 ID 获取模型详情 */
    public ApiResponse<AiModel> getModel(Long id) {
        return repository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "模型不存在"));
    }

    /** 创建新模型，自动处理默认模型逻辑（首个模型自动设为默认） */
    public ApiResponse<AiModel> createModel(AiModel model) {
        ApiResponse<AiModel> validation = validateModel(model);
        if (validation != null) return validation;
        model.setId(null);
        model.setCreatedAt(LocalDate.now());
        model.setCreatedBy(0L);

        if (model.getIsDefault()) {
            clearDefault();
        } else if (repository.count() == 0) {
            model.setIsDefault(true);
        }

        return ApiResponse.success("模型创建成功", repository.save(model));
    }

    /** 更新模型配置，设为默认时自动清除其他模型的默认标记 */
    public ApiResponse<AiModel> updateModel(Long id, AiModel updates) {
        ApiResponse<AiModel> validation = validateModel(updates);
        if (validation != null) return validation;
        return repository.findById(id)
                .map(model -> {
                    if (updates.getName() != null) model.setName(updates.getName());
                    if (updates.getProvider() != null) model.setProvider(updates.getProvider());
                    if (updates.getModelName() != null) model.setModelName(updates.getModelName());
                    if (updates.getApiUrl() != null) model.setApiUrl(updates.getApiUrl());
                    if (updates.getApiKey() != null) model.setApiKey(updates.getApiKey());
                    if (updates.getMaxTokens() != null) model.setMaxTokens(updates.getMaxTokens());
                    if (updates.getTemperature() != null) model.setTemperature(updates.getTemperature());
                    if (updates.getEnabled() != null) model.setEnabled(updates.getEnabled());
                    if (updates.getIsDefault() != null && updates.getIsDefault()) {
                        clearDefault();
                        model.setIsDefault(true);
                    }
                    return ApiResponse.success("模型更新成功", repository.save(model));
                })
                .orElseGet(() -> ApiResponse.error(404, "模型不存在"));
    }

    /** 获取默认模型 */
    public ApiResponse<AiModel> getDefaultModel() {
        return repository.findByIsDefaultTrue()
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "未设置默认模型，请在模型管理中设置"));
    }

    /** 删除模型配置 */
    public ApiResponse<Void> deleteModel(Long id) {
        if (!repository.existsById(id)) {
            return ApiResponse.error(404, "模型不存在");
        }
        repository.deleteById(id);
        return ApiResponse.success("模型已删除", null);
    }

    /** 清除当前默认模型的标记 */
    private void clearDefault() {
        repository.findByIsDefaultTrue().ifPresent(m -> {
            m.setIsDefault(false);
            repository.save(m);
        });
    }

    /**
     * 为指定模型构建 AgentScope GenerateOptions，用于覆盖 LLM 调用的参数。
     * 返回 null 表示使用全局默认配置。
     */
    public io.agentscope.core.model.GenerateOptions buildModelOptions(Long modelId) {
        if (modelId == null) return null;
        return repository.findById(modelId)
                .map(model -> {
                    var execConfig = io.agentscope.core.model.ExecutionConfig.builder()
                            .timeout(java.time.Duration.ofSeconds(llmConfig.getStreamTimeout()))
                            .maxAttempts(1)
                            .build();
                    return io.agentscope.core.model.GenerateOptions.builder()
                            .modelName(model.getModelName())
                            .apiKey(decryptApiKey(model.getApiKey()))
                            .baseUrl(model.getApiUrl())
                            .endpointPath(buildEndpointPath(model))
                            .temperature(model.getTemperature())
                            .maxTokens(model.getMaxTokens() != null && model.getMaxTokens() > 0
                                    ? model.getMaxTokens() : llmConfig.getMaxTokens())
                            .executionConfig(execConfig)
                            .build();
                })
                .orElse(null);
    }

    /** 根据 apiType 和 apiVersion 构建请求路径 */
    static String buildEndpointPath(AiModel model) {
        String type = model.getApiType();
        String version = model.getApiVersion();
        if (version == null) version = "";
        if ("anthropic".equalsIgnoreCase(type)) {
            return version + "/messages";
        }
        return version + "/chat/completions";
    }

    /**
     * 验证模型配置：调用外部 API 的 /models 接口检查连通性，并返回可用模型 ID 列表。
     * 返回成功时 data 为模型 ID 列表，返回失败时 message 包含错误描述。
     */
    public ApiResponse<List<String>> validateModel(ModelValidateRequest req) {
        String baseUrl = req.getBaseUrl();
        String apiType = req.getApiType() != null ? req.getApiType() : "openai";
        String apiVersion = req.getApiVersion() != null ? req.getApiVersion() : "/v1";
        String apiKey = req.getApiKey();

        if (baseUrl == null || baseUrl.isBlank()) {
            return ApiResponse.error(400, "请求地址不能为空");
        }

        // 构建 /models 请求 URL
        String modelsUrl = baseUrl.replaceAll("/+$", "") + apiVersion + "/models";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .timeout(java.time.Duration.ofSeconds(15));

            if ("anthropic".equalsIgnoreCase(apiType)) {
                requestBuilder.header("x-api-key", apiKey != null ? apiKey : "");
                requestBuilder.header("anthropic-version", "2023-06-01");
            } else {
                requestBuilder.header("Authorization", "Bearer " + (apiKey != null ? apiKey : ""));
            }
            requestBuilder.header("Content-Type", "application/json");

            HttpResponse<String> response = client.send(
                    requestBuilder.GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ApiResponse.error(502, "API 返回错误状态: HTTP " + response.statusCode());
            }

            // 解析模型列表
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return ApiResponse.error(502, "无法解析模型列表响应");
            }

            List<String> modelIds = new ArrayList<>();
            for (JsonNode node : data) {
                JsonNode idNode = node.get("id");
                if (idNode != null && !idNode.isNull()) {
                    modelIds.add(idNode.asText());
                }
            }

            if (modelIds.isEmpty()) {
                return ApiResponse.error(502, "未找到可用模型");
            }

            return ApiResponse.success("验证成功，共 " + modelIds.size() + " 个模型", modelIds);
        } catch (java.net.ConnectException e) {
            return ApiResponse.error(502, "无法连接到 " + baseUrl + "，请检查请求地址");
        } catch (java.net.http.HttpTimeoutException e) {
            return ApiResponse.error(502, "连接超时，请检查请求地址");
        } catch (Exception e) {
            return ApiResponse.error(502, "验证失败: " + e.getMessage());
        }
    }

    /** 解密 API Key（当前为透传，生产环境应实现加密存储） */
    private String decryptApiKey(String key) {
        return key;
    }

    /** 从 API URL 中提取基础地址（scheme + host + port） */
    static String extractBaseUrl(String apiUrl) {
        if (apiUrl == null) return null;
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            int port = uri.getPort();
            return port > 0
                    ? uri.getScheme() + "://" + uri.getHost() + ":" + port
                    : uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 API URL 中提取路径部分（含 query string） */
    static String extractPath(String apiUrl) {
        if (apiUrl == null) return null;
        try {
            java.net.URI uri = java.net.URI.create(apiUrl);
            String path = uri.getPath();
            String query = uri.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验模型配置参数合法性，返回 null 表示校验通过 */
    private ApiResponse<AiModel> validateModel(AiModel model) {
        if (model.getMaxTokens() != null && model.getMaxTokens() < 1) {
            return ApiResponse.error(400, "maxTokens 必须大于等于 1");
        }
        if (model.getTemperature() != null && (model.getTemperature() < 0 || model.getTemperature() > 2)) {
            return ApiResponse.error(400, "temperature 必须在 0 到 2 之间");
        }
        return null;
    }
}
