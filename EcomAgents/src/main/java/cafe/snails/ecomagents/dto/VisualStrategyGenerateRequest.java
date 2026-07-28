package cafe.snails.ecomagents.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * 基于认知版本生成视觉策略的请求。
 */
public record VisualStrategyGenerateRequest(
        @JsonAlias("cognition_version_id") Long cognitionVersionId,
        @JsonAlias("content_scope") List<String> contentScope
) {
}
