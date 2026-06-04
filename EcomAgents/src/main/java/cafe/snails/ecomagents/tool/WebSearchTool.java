package cafe.snails.ecomagents.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 网页搜索工具，通过 Tavily Search API 实现互联网搜索能力。
 * 以 @Tool 注解暴露给 AgentScope HarnessAgent，供 LLM 在 ReAct 循环中调用。
 */
public class WebSearchTool {

    /** 当前工具日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    /** Tavily 搜索 API 地址。 */
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    /** 单次搜索请求最大等待时间。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    /** 未指定结果数时的默认返回数量。 */
    private static final int DEFAULT_RESULTS = 3;
    /** 单次搜索允许返回的最大结果数量。 */
    private static final int MAX_RESULTS = 5;
    /** 单条搜索结果正文最大输出字符数，避免工具响应过长。 */
    private static final int MAX_RESULT_CONTENT_CHARS = 700;

    /** Tavily API Key。 */
    private final String apiKey;
    /** 用于调用 Tavily HTTP API 的 WebClient。 */
    private final WebClient webClient;
    /** 用于解析 Tavily JSON 响应。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建网页搜索工具。
     */
    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 执行互联网搜索，并将 Tavily 响应格式化为 Agent 可读的 Markdown 文本。
     */
    @Tool(name = "web_search", description = "搜索互联网获取最新信息。查询天气、新闻、价格、汇率、实时数据或知识范围外的信息时必须使用此工具，不要使用本地 shell")
    public String search(
            @ToolParam(name = "query", description = "搜索关键词，应该简洁明确") String query,
            @ToolParam(name = "max_results", description = "返回结果数量，默认 3，最大 5") Integer maxResults) {
        if (query == null || query.isBlank()) {
            return "搜索失败：搜索关键词不能为空";
        }

        int count = (maxResults != null && maxResults > 0) ? Math.min(maxResults, MAX_RESULTS) : DEFAULT_RESULTS;

        try {
            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "search_depth", "advanced",
                    "include_answer", true,
                    "max_results", count
            );

            String responseJson = webClient.post()
                    .uri(TAVILY_API_URL)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (responseJson == null || responseJson.isBlank()) {
                return "搜索失败：Tavily API 返回了空响应";
            }

            return formatResults(query, responseJson);

        } catch (Exception e) {
            log.error("Tavily search failed for query '{}': {}", query, e.getMessage());
            return "搜索失败：" + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * 将 Tavily JSON 响应整理为摘要和结果列表。
     */
    private String formatResults(String query, String responseJson) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseJson, Map.class);
            StringBuilder sb = new StringBuilder();

            sb.append("## 搜索结果：").append(query).append("\n\n");

            // 摘要
            Object answer = response.get("answer");
            if (answer != null && !answer.toString().isBlank()) {
                sb.append("**摘要**: ").append(truncate(answer.toString(), 900)).append("\n\n");
            }

            // 结果列表
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) {
                sb.append("未找到相关结果。\n");
                return sb.toString();
            }

            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> result = results.get(i);
                sb.append("### ").append(i + 1).append(". ")
                        .append(result.getOrDefault("title", "无标题"))
                        .append("\n\n");
                sb.append("- **链接**: ").append(result.getOrDefault("url", "")).append("\n");
                sb.append("- **内容**: ")
                        .append(truncate(String.valueOf(result.getOrDefault("content", "无内容")), MAX_RESULT_CONTENT_CHARS))
                        .append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("Failed to format Tavily results: {}", e.getMessage());
            return "搜索完成，但解析结果时出错：" + e.getMessage();
        }
    }

    /**
     * 截断过长文本，控制工具返回内容长度。
     */
    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
