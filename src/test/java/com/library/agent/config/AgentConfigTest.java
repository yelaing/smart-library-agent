package com.library.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.library.agent.service.AgentSessionManager;
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
 * <p>主要目的是守卫工具集完整性与会话隔离：LibraryTool 的构造依赖在重构中变更过
 * （EmbeddingService + VectorStore 换成 RecommendationService），一旦漏注册或注册失败，
 * 这里会直接失败而不是等到线上发现 Agent 少了能力；而 agent 实例若被所有会话共享，
 * 对话记忆会互相污染。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Agent 装配集成测试")
class AgentConfigTest {

    @Autowired
    private Toolkit toolkit;
    @Autowired
    private AgentSessionManager sessionManager;

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
    @DisplayName("同一会话复用同一 agent，不同会话实例隔离")
    void forSession_shouldIsolateDifferentSessions() {
        ReActAgent first = sessionManager.forSession("session-a");
        ReActAgent again = sessionManager.forSession("session-a");
        ReActAgent other = sessionManager.forSession("session-b");

        assertSame(first, again, "同一会话应复用实例，否则多轮对话的记忆会丢失");
        assertNotSame(first, other, "不同会话必须是不同实例，否则两个读者的对话记忆会互相污染");
        assertEquals("图书馆助手", first.getName());
    }

    @Test
    @DisplayName("未传 sessionId 时返回一次性实例，不与任何调用共享记忆")
    void forSession_shouldReturnFreshAgent_whenSessionIdMissing() {
        assertNotSame(sessionManager.forSession(null), sessionManager.forSession(null));
        assertNotSame(sessionManager.forSession("  "), sessionManager.forSession("  "));
    }
}
