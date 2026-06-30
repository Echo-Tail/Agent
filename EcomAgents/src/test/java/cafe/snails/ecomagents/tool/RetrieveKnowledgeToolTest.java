package cafe.snails.ecomagents.tool;

import cafe.snails.ecomagents.service.KnowledgeBaseService;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识检索工具测试，验证未绑定知识库和正常检索委托行为。
 */
class RetrieveKnowledgeToolTest {

    @Test
    void registerTool_shouldExposeRetrieveKnowledgeToAgentScopeToolkit() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        RetrieveKnowledgeTool tool = new RetrieveKnowledgeTool(knowledgeBaseService, List.of(1L));
        Toolkit toolkit = new Toolkit();

        toolkit.registerTool(tool);

        assertTrue(toolkit.getToolNames().contains("retrieve_knowledge"));
        assertNotNull(toolkit.getTool("retrieve_knowledge"));
    }

    @Test
    void retrieveKnowledge_shouldUseBoundKnowledgeBases() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(knowledgeBaseService.buildKnowledgeContext(List.of(1L, 3L), "shipping policy"))
                .thenReturn("matched context");
        RetrieveKnowledgeTool tool = new RetrieveKnowledgeTool(knowledgeBaseService, List.of(1L, 3L));

        String result = tool.retrieve_knowledge("shipping policy");

        assertEquals("matched context", result);
    }
}
