package com.library.agent.exception;

/**
 * Agent 单次调用超时。由 ChatController 在 Reactor 链路上把 {@link java.util.concurrent.TimeoutException}
 * 转换而来，避免受检异常被 {@code block()} 包成难以识别的 ReactiveException。
 */
public class AgentTimeoutException extends RuntimeException {

    public AgentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
