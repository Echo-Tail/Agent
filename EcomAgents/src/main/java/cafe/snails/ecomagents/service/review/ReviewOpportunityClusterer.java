package cafe.snails.ecomagents.service.review;

import cafe.snails.ecomagents.model.review.*;
import cafe.snails.ecomagents.service.*;
import com.fasterxml.jackson.databind.*;
import io.agentscope.core.model.GenerateOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOpportunityClusterer {
    private static final int MAX_CLUSTER_INPUT = 50;
    private final LlmService llmService;
    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;

    public List<Cluster> cluster(ReviewAnalysisRun run, List<ReviewInsight> insights) {
        GenerateOptions options = aiModelService.buildModelOptions(run.getModelId());
        if (options == null) return fallback(insights);
        List<Cluster> result = new ArrayList<>();
        for (int from = 0; from < insights.size(); from += MAX_CLUSTER_INPUT) {
            List<ReviewInsight> chunk = insights.subList(from, Math.min(from + MAX_CLUSTER_INPUT, insights.size()));
            try {
                String response = llmService.syncChat(systemPrompt(run.getRolePrompt()),
                        List.of(Map.of("role", "user", "content", input(chunk))), options);
                result.addAll(parse(response, chunk));
            } catch (Exception e) {
                log.warn("Opportunity clustering fallback used: runId={}, error={}", run.getId(), e.getMessage());
                result.addAll(fallback(chunk));
            }
        }
        return result;
    }

    private String systemPrompt(String rolePrompt) {
        return rolePrompt + """

                Group semantically equivalent car stereo user problems into product improvement opportunities.
                Return JSON only: {"schema_version":"review_opportunity_v1","clusters":[
                  {"title":"...","insight_ids":[1,2],"recommended_action":"...","rationale":"..."}
                ]}.
                Every supplied insight_id must appear exactly once. Never add IDs.
                Keep materially different failure modes in separate clusters.
                Write title, recommended_action and rationale in concise Simplified Chinese.
                Do not translate or rewrite evidence_quote.
                """;
    }

    private String input(List<ReviewInsight> insights) throws Exception {
        List<Map<String, Object>> rows = insights.stream().map(value -> Map.<String, Object>of(
                "insight_id", value.getId(),
                "user_problem", value.getUserProblem(),
                "evidence_quote", value.getEvidenceQuote(),
                "severity", value.getSeverity(),
                "improvement_action", value.getImprovementAction())).toList();
        return objectMapper.writeValueAsString(rows);
    }

    private List<Cluster> parse(String response, List<ReviewInsight> insights) throws Exception {
        int start = response == null ? -1 : response.indexOf('{');
        int end = response == null ? -1 : response.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("聚类响应不包含 JSON");
        JsonNode root = objectMapper.readTree(response.substring(start, end + 1));
        if (!"review_opportunity_v1".equals(root.path("schema_version").asText())
                || !root.path("clusters").isArray()) {
            throw new IllegalArgumentException("聚类响应 schema 无效");
        }
        Set<Long> allowed = new LinkedHashSet<>();
        insights.forEach(value -> allowed.add(value.getId()));
        Set<Long> returned = new HashSet<>();
        List<Cluster> clusters = new ArrayList<>();
        for (JsonNode node : root.path("clusters")) {
            String title = required(node, "title");
            String action = required(node, "recommended_action");
            String rationale = required(node, "rationale");
            if (!containsChinese(title) || !containsChinese(action)) {
                throw new IllegalArgumentException("机会标题和改进建议必须使用中文");
            }
            if (!node.path("insight_ids").isArray() || node.path("insight_ids").isEmpty()) {
                throw new IllegalArgumentException("insight_ids 不能为空");
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode idNode : node.path("insight_ids")) {
                if (!idNode.canConvertToLong()) throw new IllegalArgumentException("insight_id 必须为整数");
                long id = idNode.asLong();
                if (!allowed.contains(id) || !returned.add(id)) {
                    throw new IllegalArgumentException("insight_id 越界或重复");
                }
                ids.add(id);
            }
            clusters.add(new Cluster(title, ids, action, rationale));
        }
        if (!returned.equals(allowed)) throw new IllegalArgumentException("聚类未覆盖全部 insight_id");
        return clusters;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value;
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private List<Cluster> fallback(List<ReviewInsight> insights) {
        Map<String, List<ReviewInsight>> groups = new LinkedHashMap<>();
        for (var insight : insights) {
            String key = insight.getUserProblem().toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(insight);
        }
        return groups.values().stream().map(group -> {
            var first = group.get(0);
            return new Cluster(first.getUserProblem(), group.stream().map(ReviewInsight::getId).toList(),
                    first.getImprovementAction(), "根据归一化用户问题自动聚合。");
        }).toList();
    }

    public record Cluster(String title, List<Long> insightIds, String recommendedAction, String rationale) {}
}
