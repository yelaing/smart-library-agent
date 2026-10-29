package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 同一 sessionId 并发请求的记忆串台问题：复现 + 验证修复。
 *
 * <p>共享状态用的是 AgentScope 真实的 {@link InMemoryMemory}，不是模拟对象 ——
 * 它底层是 {@code CopyOnWriteArrayList}，并发写入不会损坏数据、也不会抛异常，
 * 所以这个问题不会被任何「异常率」指标暴露出来，只表现为上下文交错：
 * A 的回复落进 B 的对话历史里。</p>
 */
@DisplayName("会话串行化测试")
class SessionSerializationTest {

    @Test
    @DisplayName("复现：没有闸门时，两个并发请求的问题会挤在一起，回复穿插写进同一份记忆")
    void withoutGate_messagesInterleave() throws Exception {
        InMemoryMemory memory = new InMemoryMemory();
        CountDownLatch bothAsked = new CountDownLatch(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> turn(memory, "A", bothAsked));
            pool.submit(() -> turn(memory, "B", bothAsked));
            assertTrue(bothAsked.await(5, TimeUnit.SECONDS), "两个请求都应写入问题");
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }

        List<String> actual = texts(memory);
        assertEquals(4, actual.size());
        // 两个请求的「问题」相邻，说明两次对话已经交织在同一份历史里
        assertTrue(actual.get(0).endsWith("问题") && actual.get(1).endsWith("问题"),
                "没有闸门时两个请求的问题会连着写进去，实际 " + actual);
    }

    @Test
    @DisplayName("修复：会话闸门下同一 session 的两轮对话严格分段，不交错")
    void withGate_turnsAreSequential() throws Exception {
        InMemoryMemory memory = new InMemoryMemory();
        AgentSessionManager.SessionContext context =
                new AgentSessionManager.SessionContext(null, new Semaphore(1));
        CyclicBarrier start = new CyclicBarrier(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> gatedTurn(memory, context, "A", start));
            pool.submit(() -> gatedTurn(memory, context, "B", start));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        List<String> actual = texts(memory);
        // 只可能是「A 整轮 + B 整轮」或「B 整轮 + A 整轮」，不允许交叉
        List<String> aThenB = List.of("A-问题", "A-回复", "B-问题", "B-回复");
        List<String> bThenA = List.of("B-问题", "B-回复", "A-问题", "A-回复");
        assertTrue(actual.equals(aThenB) || actual.equals(bThenA),
                "闸门生效后每轮对话应自成一段，实际 " + actual);
    }

    @Test
    @DisplayName("闸门本身：任意并发下临界区最多只有一个线程")
    void gate_shouldAllowOnlyOneThreadInside() throws Exception {
        AgentSessionManager.SessionContext context =
                new AgentSessionManager.SessionContext(null, new Semaphore(1));
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    for (int round = 0; round < 50; round++) {
                        context.acquire();
                        try {
                            int current = inside.incrementAndGet();
                            maxInside.accumulateAndGet(current, Math::max);
                            Thread.yield();
                            inside.decrementAndGet();
                        } finally {
                            context.release();
                        }
                    }
                    done.countDown();
                });
            }
            assertTrue(done.await(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, maxInside.get(), "临界区内不应同时出现两个线程");
    }

    /** 无闸门的一轮：先写问题，等两个请求都写完，再写回复。 */
    private void turn(InMemoryMemory memory, String tag, CountDownLatch bothAsked) {
        try {
            memory.addMessage(msg(MsgRole.USER, tag + "-问题"));
            bothAsked.countDown();
            bothAsked.await(5, TimeUnit.SECONDS);
            Thread.sleep(20);
            memory.addMessage(msg(MsgRole.ASSISTANT, tag + "-回复"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void gatedTurn(InMemoryMemory memory, AgentSessionManager.SessionContext context,
                           String tag, CyclicBarrier start) {
        try {
            start.await(5, TimeUnit.SECONDS);   // 让两轮尽量同时发起
            context.acquire();
            try {
                memory.addMessage(msg(MsgRole.USER, tag + "-问题"));
                Thread.sleep(30);                // 模拟 LLM 往返
                memory.addMessage(msg(MsgRole.ASSISTANT, tag + "-回复"));
            } finally {
                context.release();
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    private Msg msg(MsgRole role, String text) {
        return Msg.builder().role(role).content(TextBlock.builder().text(text).build()).build();
    }

    private List<String> texts(InMemoryMemory memory) {
        List<String> out = new ArrayList<>();
        for (Msg m : memory.getMessages()) {
            out.add(m.getContent().stream()
                    .filter(b -> b instanceof TextBlock)
                    .map(b -> ((TextBlock) b).getText())
                    .reduce("", (a, b) -> a + b));
        }
        return out;
    }
}
