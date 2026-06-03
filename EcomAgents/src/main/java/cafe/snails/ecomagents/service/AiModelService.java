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

    /** 获取已启用的图片生成模型（modelType=IMAGE） */
    public ApiResponse<List<AiModel>> getEnabledImageModels() {
        return ApiResponse.success(repository.findByModelTypeAndEnabled("IMAGE", true));
    }

    /** 根据 ID 获取模型详情 */
    public ApiResponse<AiModel> getModel(Long id) {
        return repository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "模型不存在"));
    }

    /** 创建新模型，自动处理默认模型逻辑（首个模型自动设为默认） */
    public ApiResponse<AiModel> createModel(AiModel model) {
        normalizeModelConfig(model);
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
        boolean shouldUpdateApiType = updates.getApiType() != null || updates.getProvider() != null;
        boolean shouldUpdateApiVersion = updates.getApiVersion() != null || updates.getApiUrl() != null || updates.getProvider() != null;
        normalizeModelConfig(updates);
        ApiResponse<AiModel> validation = validateModel(updates);
        if (validation != null) return validation;
        return repository.findById(id)
                .map(model -> {
                    if (updates.getName() != null) model.setName(updates.getName());
                    if (updates.getProvider() != null) model.setProvider(updates.getProvider());
                    if (updates.getModelName() != null) model.setModelName(updates.getModelName());
                    if (updates.getApiUrl() != null) model.setApiUrl(updates.getApiUrl());
                    if (updates.getApiKey() != null) model.setApiKey(updates.getApiKey());
                    if (shouldUpdateApiType) model.setApiType(updates.getApiType());
                    if (shouldUpdateApiVersion) model.setApiVersion(updates.getApiVersion());
                    if (updates.getMaxTokens() != null) model.setMaxTokens(updates.getMaxTokens());
                    if (updates.getTemperature() != null) model.setTemperature(updates.getTemperature());
                    if (updates.getModelType() != null) model.setModelType(updates.getModelType());
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

    /** 根据 apiType 和 API 地址构建请求路径 */
    static String buildEndpointPath(AiModel model) {
        String type = normalizeApiType(model.getApiType(), model.getProvider());
        String version = normalizePath(model.getApiVersion());
        if (!version.isBlank()) {
            return "anthropic".equalsIgnoreCase(type)
                    ? version + "/messages"
                    : version + "/chat/completions";
        }

        String basePath = extractPathWithoutQuery(model.getApiUrl());
        if (basePath == null) {
            return "anthropic".equalsIgnoreCase(type) ? "/messages" : "/chat/completions";
        }
        boolean urlAlreadyHasVersion = basePath != null && (
                basePath.endsWith("/v1")
                        || basePath.endsWith("/compatible-mode/v1")
                        || basePath.matches(".*/v\\d+(?:beta)?$"));

        if ("anthropic".equalsIgnoreCase(type)) {
            return urlAlreadyHasVersion ? "/messages" : "/v1/messages";
        }
        if ("deepseek".equalsIgnoreCase(model.getProvider())) {
            return "/chat/completions";
        }
        return urlAlreadyHasVersion ? "/chat/completions" : "/v1/chat/completions";
    }

    /**
     * 验证模型配置：调用外部 API 的 /models 接口检查连通性，并返回可用模型 ID 列表。
     * 返回成功时 data 为模型 ID 列表，返回失败时 message 包含错误描述。
     */
    public ApiResponse<List<String>> validateModel(ModelValidateRequest req) {
        String baseUrl = trimTrailingSlash(req.getBaseUrl());
        String provider = req.getProvider() != null ? req.getProvider().trim().toLowerCase() : null;
        String apiType = normalizeApiType(req.getApiType(), provider);
        String apiVersion = normalizePath(req.getApiVersion());
        String apiKey = req.getApiKey();

        if (baseUrl == null || baseUrl.isBlank()) {
            return ApiResponse.error(400, "请求地址不能为空");
        }

        String modelsUrl;
        try {
            modelsUrl = buildModelsUrl(baseUrl, provider, apiType, apiVersion);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }

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
                return ApiResponse.error(502, describeValidationHttpError(response.statusCode(), baseUrl, modelsUrl));
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
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, "请求地址格式不正确，请填写供应商根地址，例如 https://api.openai.com");
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
        String apiUrl = trimTrailingSlash(model.getApiUrl());
        if (apiUrl != null && !apiUrl.isBlank()) {
            ApiResponse<AiModel> urlError = validateApiUrl(model.getProvider(), apiUrl);
            if (urlError != null) return urlError;
            model.setApiUrl(apiUrl);
        }
        if (model.getMaxTokens() != null && model.getMaxTokens() < 1) {
            return ApiResponse.error(400, "maxTokens 必须大于等于 1");
        }
        if (model.getTemperature() != null && (model.getTemperature() < 0 || model.getTemperature() > 2)) {
            return ApiResponse.error(400, "temperature 必须在 0 到 2 之间");
        }
        return null;
    }

    private static void normalizeModelConfig(AiModel model) {
        if (model == null) return;
        if (model.getProvider() != null) {
            model.setProvider(model.getProvider().trim().toLowerCase());
        }
        model.setApiUrl(trimTrailingSlash(model.getApiUrl()));
        model.setApiType(normalizeApiType(model.getApiType(), model.getProvider()));
        model.setApiVersion("");
    }

    private static String normalizeApiType(String apiType, String provider) {
        if ("anthropic".equalsIgnoreCase(provider)) return "anthropic";
        if (apiType == null || apiType.isBlank()) return "openai";
        return apiType.trim().toLowerCase();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("/+$", "");
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.trim();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized.replaceAll("/+$", "");
    }

    private static String buildModelsUrl(String baseUrl, String provider, String apiType, String apiVersion) {
        URI uri = URI.create(baseUrl);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("请求地址格式不正确，请填写完整地址，例如 https://api.openai.com");
        }
        String basePath = normalizePath(uri.getPath());
        if (basePath.endsWith("/models")) {
            throw new IllegalArgumentException("请求地址请填写供应商根地址，不要包含 /models");
        }
        if (basePath.endsWith("/chat/completions") || basePath.endsWith("/messages")) {
            throw new IllegalArgumentException("请求地址请填写供应商根地址，不要包含具体接口路径");
        }

        if (!apiVersion.isBlank()) {
            return baseUrl + apiVersion + "/models";
        }
        boolean urlAlreadyHasVersion = basePath.endsWith("/v1")
                || basePath.endsWith("/compatible-mode/v1")
                || basePath.matches(".*/v\\d+(?:beta)?$");
        if (urlAlreadyHasVersion) {
            return baseUrl + "/models";
        }
        if ("deepseek".equalsIgnoreCase(provider)) {
            return baseUrl + "/models";
        }
        return "anthropic".equalsIgnoreCase(apiType)
                ? baseUrl + "/v1/models"
                : baseUrl + "/v1/models";
    }

    private static String describeValidationHttpError(int statusCode, String baseUrl, String modelsUrl) {
        if (statusCode == 401 || statusCode == 403) {
            return "API 认证失败，请检查 API Key、供应商和请求地址是否匹配";
        }
        if (statusCode == 404) {
            return "模型列表接口不存在，请检查请求地址是否应包含或去掉 /v1。当前检测地址：" + modelsUrl;
        }
        return "API 返回错误状态: HTTP " + statusCode + "，请检查请求地址、供应商和 API Key";
    }

    private static ApiResponse<AiModel> validateApiUrl(String provider, String apiUrl) {
        URI uri;
        try {
            uri = URI.create(apiUrl);
        } catch (Exception e) {
            return ApiResponse.error(400, "请求地址格式不正确，请填写完整地址，例如 https://api.openai.com");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return ApiResponse.error(400, "请求地址格式不正确，请填写完整地址，例如 https://api.openai.com");
        }
        String path = normalizePath(uri.getPath());
        if (path.endsWith("/models") || path.endsWith("/chat/completions") || path.endsWith("/messages")) {
            return ApiResponse.error(400, "请求地址请填写供应商根地址，不要包含 /models、/chat/completions 或 /messages");
        }
        String host = uri.getHost().toLowerCase();
        if ("openai".equalsIgnoreCase(provider) && !host.equals("api.openai.com")) {
            return ApiResponse.error(400, "OpenAI 供应商默认地址为 https://api.openai.com；如果使用兼容接口，请选择“其它”或对应供应商");
        }
        if ("anthropic".equalsIgnoreCase(provider) && !host.equals("api.anthropic.com")) {
            return ApiResponse.error(400, "Anthropic 供应商默认地址为 https://api.anthropic.com；请检查供应商或请求地址");
        }
        if ("deepseek".equalsIgnoreCase(provider) && !host.equals("api.deepseek.com")) {
            return ApiResponse.error(400, "DeepSeek 供应商默认地址为 https://api.deepseek.com；请检查供应商或请求地址");
        }
        if ("qwen".equalsIgnoreCase(provider) && !host.equals("dashscope.aliyuncs.com")) {
            return ApiResponse.error(400, "阿里百炼供应商默认地址为 https://dashscope.aliyuncs.com/compatible-mode/v1；请检查供应商或请求地址");
        }
        return null;
    }

    private static String extractPathWithoutQuery(String apiUrl) {
        String path = extractPath(apiUrl);
        if (path == null) return null;
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }
}
