package cafe.snails.ecomagents.service.rag;

import java.util.Map;

public record KnowledgeUnit(
        Long documentId,
        Long knowledgeBaseId,
        String fileName,
        String fileType,
        String unitType,
        String title,
        String content,
        Map<String, Object> metadata,
        String sourceLocation) {
}
