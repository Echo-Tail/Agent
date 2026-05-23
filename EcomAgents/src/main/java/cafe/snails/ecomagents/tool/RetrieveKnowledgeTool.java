package cafe.snails.ecomagents.tool;

import cafe.snails.ecomagents.service.KnowledgeBaseService;

/**
 * 知识检索工具 — AGENTIC RAG 模式下供 Agent 自主调用。
 * <p>Agent 在对话过程中可通过此工具查询绑定的知识库，获取相关文档片段。</p>
 */
public class RetrieveKnowledgeTool {

    private final KnowledgeBaseService knowledgeBaseService;
    private final java.util.List<Long> kbIds;

    public RetrieveKnowledgeTool(KnowledgeBaseService knowledgeBaseService, java.util.List<Long> kbIds) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbIds = kbIds;
    }

    /**
     * 从关联的知识库中检索与 query 相关的内容。
     *
     * @param query 检索关键词或问题
     * @return 匹配的文档片段
     */
    public String retrieve_knowledge(String query) {
        if (kbIds == null || kbIds.isEmpty()) {
            return "该 Agent 未关联任何知识库";
        }
        return knowledgeBaseService.buildKnowledgeContext(kbIds, query);
    }
}
