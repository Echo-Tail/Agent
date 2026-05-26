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

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int DEFAULT_RESULTS = 3;
    private static final int MAX_RESULTS = 5;
    private static final int MAX_RESULT_CONTENT_CHARS = 700;

    private final String apiKey;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.create();
        this.objectMapper = new ObjectMapper();
    }

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

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
