package cafe.snails.ecomagents.tool;

import cafe.snails.ecomagents.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 * Knowledge retrieval tool exposed to AgentScope in AGENTIC RAG mode.
 */
public class RetrieveKnowledgeTool {

    /** 知识库服务，用于构建 RAG 上下文。 */
    private final KnowledgeBaseService knowledgeBaseService;
    /** 当前 Agent 绑定的知识库 ID 列表。 */
    private final List<Long> kbIds;

    /**
     * 创建知识库检索工具实例。
     */
    public RetrieveKnowledgeTool(KnowledgeBaseService knowledgeBaseService, List<Long> kbIds) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbIds = kbIds;
    }

    /**
     * 根据用户问题检索当前 Agent 绑定知识库中的相关上下文。
     */
    @Tool(
            name = "retrieve_knowledge",
            description = "Retrieve relevant content from the knowledge bases bound to this agent. "
                    + "Use this when the user asks about uploaded documents or stored knowledge.")
    public String retrieve_knowledge(
            @ToolParam(
                    name = "query",
                    description = "The search query or user question for knowledge-base retrieval")
            String query) {
        if (kbIds == null || kbIds.isEmpty()) {
            return "当前 Agent 未绑定任何知识库。";
        }
        return knowledgeBaseService.buildKnowledgeContext(kbIds, query);
    }
}
