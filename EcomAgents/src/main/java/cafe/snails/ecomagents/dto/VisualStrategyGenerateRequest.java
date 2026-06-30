package cafe.snails.ecomagents.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public record VisualStrategyGenerateRequest(
        @JsonAlias("cognition_version_id") Long cognitionVersionId,
        @JsonAlias("content_scope") List<String> contentScope
) {
}