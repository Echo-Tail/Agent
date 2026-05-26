package cafe.snails.ecomagents.tool;

import cafe.snails.ecomagents.service.KnowledgeBaseService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.List;

/**
 * Knowledge retrieval tool exposed to AgentScope in AGENTIC RAG mode.
 */
public class RetrieveKnowledgeTool {

    private final KnowledgeBaseService knowledgeBaseService;
    private final List<Long> kbIds;

    public RetrieveKnowledgeTool(KnowledgeBaseService knowledgeBaseService, List<Long> kbIds) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbIds = kbIds;
    }

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
