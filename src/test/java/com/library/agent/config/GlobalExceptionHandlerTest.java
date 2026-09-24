package com.library.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.library.agent.dto.ErrorResponse;
import com.library.agent.exception.AgentTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("全局异常处理测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("参数错误 → 400 + 错误码 40001")
    void shouldMapIllegalArgumentToInvalidRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("messages 不能为空"), request("/v1/chat/completions"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = requireBody(response);
        assertEquals(40001, body.code());
        assertEquals("messages 不能为空", body.message());
        assertEquals("/v1/chat/completions", body.path());
        assertEquals(400, body.status());
    }

    @Test
    @DisplayName("Agent 超时 → 504 + 错误码 50401")
    void shouldMapAgentTimeoutToGatewayTimeout() {
        ResponseEntity<ErrorResponse> response = handler.handleTimeout(
                new AgentTimeoutException("Agent 调用超时（60 秒），请稍后重试", new RuntimeException()), request("/v1/chat/completions"));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        ErrorResponse body = requireBody(response);
        assertEquals(50401, body.code());
        assertEquals("Agent 调用超时（60 秒），请稍后重试", body.message());
    }

    @Test
    @DisplayName("未知异常 → 500 + 错误码 50000，且不把内部细节泄漏给调用方")
    void shouldNotLeakInternalDetailsForUnknownException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
                new RuntimeException("jdbc:mysql://root:secret@db:3306 连接失败"), request("/v1/chat/completions"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = requireBody(response);
        assertEquals(50000, body.code());
        assertEquals("服务器内部错误，请稍后重试", body.message());
    }

    @Test
    @DisplayName("错误响应携带当前请求的 traceId")
    void shouldIncludeTraceIdFromMdc() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-42");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new IllegalArgumentException("x"), request("/x"));

        assertEquals("trace-42", requireBody(response).traceId());
    }

    private ErrorResponse requireBody(ResponseEntity<ErrorResponse> response) {
        ErrorResponse body = response.getBody();
        assertNotNull(body, "错误响应体不能为空");
        return body;
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }
}
