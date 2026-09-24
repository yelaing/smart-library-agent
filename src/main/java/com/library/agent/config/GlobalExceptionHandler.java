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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * Spring MVC 对下列客户端错误本应直接返回对应状态码，但 {@code @ExceptionHandler(Exception.class)}
     * 会把它们统一吞成 500，客户端因此无法区分"自己请求写错了"和"服务端出故障了"。
     * 这里逐个显式映射，保留正确的语义。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在: {}", request.getRequestURI());
        return build(ErrorCode.NOT_FOUND, "请求的路径不存在", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                               HttpServletRequest request) {
        log.warn("请求方法不被支持: {} {}", request.getMethod(), request.getRequestURI());
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "该路径不支持 " + request.getMethod() + " 方法", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                                    HttpServletRequest request) {
        log.warn("不支持的 Content-Type: {}", e.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "请求体 Content-Type 不被支持", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e,
                                                              HttpServletRequest request) {
        // 只记录异常类型，不回显解析细节，避免把内部结构泄漏给调用方
        log.warn("请求体无法解析: {}", e.getClass().getSimpleName());
        return build(ErrorCode.INVALID_REQUEST, "请求体不是合法的 JSON", request);
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
