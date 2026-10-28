package com.library.agent.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 按会话维护 ReActAgent 实例。
 *
 * <p>把 agent 直接做成单例 Bean 有两个问题：{@code InMemoryMemory} 挂在 agent 实例上，
 * 单例意味着所有调用者共享同一份对话记忆，A 用户说过的话会出现在 B 用户的上下文里；
 * 且并发请求会同时写同一份记忆。改为按 sessionId 取实例后记忆随会话隔离，
 * 长时间不活跃的会话由 Caffeine 按访问时间过期回收。</p>
 */
@Component
public class AgentSessionManager {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionManager.class);

    private final Cache<String, ReActAgent> sessions;
    private final DashScopeChatModel model;
    private final Toolkit toolkit;
    private final String agentName;
    private final String sysPrompt;

    public AgentSessionManager(DashScopeChatModel model,
                               Toolkit toolkit,
                               @Value("${agentscope.agent.name:图书馆助手}") String agentName,
                               @Value("${agentscope.agent.sys-prompt:你是学校图书馆的智能助手，帮助读者查书、借书、还书。}")
                               String sysPrompt,
                               @Value("${library.session.max:500}") long maxSessions,
                               @Value("${library.session.ttl:30m}") Duration ttl) {
        this.model = model;
        this.toolkit = toolkit;
        this.agentName = agentName;
        this.sysPrompt = sysPrompt;
        this.sessions = Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(ttl)
                .build();
    }

    public String agentName() {
        return agentName;
    }

    /**
     * 取该会话的 agent。未传 sessionId 时返回一次性实例 —— 单轮无状态请求不需要记忆，
     * 复用共享实例反而会让不同调用者互相污染。
     */
    public ReActAgent forSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return create();
        }
        return sessions.get(sessionId, id -> create());
    }

    private ReActAgent create() {
        log.debug("创建 agent 实例");
        return ReActAgent.builder()
                .name(agentName)
                .sysPrompt(sysPrompt)
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .build();
    }
}
