package com.library.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Agent 装配的集成测试。
 *
 * <p>主要目的是守卫工具集完整性：LibraryTool 的构造依赖在重构中变更过
 * （EmbeddingService + VectorStore 换成 RecommendationService），
 * 一旦漏注册或注册失败，这里会直接失败而不是等到线上发现 Agent 少了能力。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Agent 装配集成测试")
class AgentConfigTest {

    @Autowired
    private Toolkit toolkit;
    @Autowired
    private ReActAgent reActAgent;

    @Test
    @DisplayName("五个图书工具全部注册到 Toolkit")
    void toolkit_shouldRegisterAllFiveLibraryTools() {
        Set<String> toolNames = toolkit.getToolNames();

        assertEquals(Set.of("recommend_book", "search_book", "query_stock", "borrow_book", "return_book"),
                toolNames, "工具集应恰好包含这五个，多一个或少一个都说明装配被改动了");
    }

    @Test
    @DisplayName("每个工具都生成了可供 LLM 调用的 schema")
    void eachTool_shouldExposeCallableSchema() {
        assertEquals(5, toolkit.getToolSchemas().size());
    }

    @Test
    @DisplayName("ReActAgent 使用配置中的 agent 名称")
    void reActAgent_shouldUseConfiguredName() {
        assertNotNull(reActAgent);
        assertEquals("图书馆助手", reActAgent.getName());
    }
}
