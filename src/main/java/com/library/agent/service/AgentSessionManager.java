package com.library.agent.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import java.time.Duration;
import java.util.concurrent.Semaphore;
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

    private final Cache<String, SessionContext> contexts;
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
        this.contexts = Caffeine.newBuilder()
                .maximumSize(maxSessions)
                .expireAfterAccess(ttl)
                .build();
    }

    public String agentName() {
        return agentName;
    }

    /**
     * 会话上下文：agent 实例 + 一把保证同会话串行的闸门。
     *
     * <p>{@code InMemoryMemory} 内部是 {@code CopyOnWriteArrayList}，并发写入不会损坏数据，
     * 但两个并发请求的「用户消息 / 助手回复」会交错落进同一份记忆，导致上下文串台（A 的问题
     * 出现在 B 的历史里）。因此同一 sessionId 的请求必须串行执行。</p>
     *
     * <p>选用信号量而不是 {@code synchronized}/{@code ReentrantLock}：SSE 流的获取与释放
     * 发生在不同线程，{@code ReentrantLock} 要求同线程释放，信号量没有这个约束。</p>
     */
    public record SessionContext(ReActAgent agent, Semaphore gate) {

        /** 阻塞直到拿到该会话的执行权；调用方必须在 finally 中 {@link #release()}。 */
        public void acquire() {
            gate.acquireUninterruptibly();
        }

        public void release() {
            gate.release();
        }
    }

    public SessionContext forSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            // 无 sessionId 的单轮请求：各自独占一个实例和闸门，彼此没有共享状态
            return new SessionContext(create(), new Semaphore(1));
        }
        return contexts.get(sessionId, id -> new SessionContext(create(), new Semaphore(1)));
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
