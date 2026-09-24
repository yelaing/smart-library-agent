package com.library.agent.config;

import com.library.agent.dto.ErrorResponse;
import com.library.agent.exception.AgentTimeoutException;
import com.library.agent.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。所有错误响应统一为 {@link ErrorResponse}，并带业务错误码与 traceId。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("请求参数错误: {}", e.getMessage());
        return build(ErrorCode.INVALID_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(AgentTimeoutException e, HttpServletRequest request) {
        log.error("Agent 调用超时: {}", e.getMessage());
        return build(ErrorCode.AGENT_TIMEOUT, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("服务器内部错误", e);
        return build(ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试", request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now().toString(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getStatus().getReasonPhrase(),
                message,
                MDC.get(TraceIdFilter.MDC_KEY),
                request.getRequestURI());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
