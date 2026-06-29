package cafe.snails.ecomagents.service.rag;

import java.util.Map;

/**
 * 知识单元记录，表示文档被解析后可独立索引和检索的最小内容块。
 */
public record KnowledgeUnit(
        Long documentId,
        Long knowledgeBaseId,
        String fileName,
        String fileType,
        String unitType,
        String title,
        String content,
        Map<String, Object> metadata,
        String sourceLocation,
        String parentContent,
        String parentSourceLocation) {

    /**
     * 创建不包含父块上下文的知识单元，兼容旧的解析路径。
     */
    public KnowledgeUnit(Long documentId, Long knowledgeBaseId, String fileName,
                         String fileType, String unitType, String title,
                         String content, Map<String, Object> metadata, String sourceLocation) {
        this(documentId, knowledgeBaseId, fileName, fileType, unitType, title,
                content, metadata, sourceLocation, null, null);
    }
}
