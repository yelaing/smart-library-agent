package com.library.agent.dto;

/**
 * 统一错误响应体。{@code message} 沿用旧字段名，保证既有调用方不受影响。
 *
 * @param code      业务错误码，见 {@link com.library.agent.exception.ErrorCode}
 * @param traceId   当前请求的 traceId，便于按 id 检索日志
 * @param path      出错的请求路径
 */
public record ErrorResponse(String timestamp, int status, int code, String error,
                            String message, String traceId, String path) {
}
