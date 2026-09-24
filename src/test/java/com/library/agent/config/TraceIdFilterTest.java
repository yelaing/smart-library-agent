package com.library.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("traceId 过滤器测试")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("请求头带 traceId 时原样透传并回写响应头")
    void shouldReuseIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("abc123", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("请求头无 traceId 时自动生成 16 位并回写响应头")
    void shouldGenerateTraceId_whenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(generated);
        assertEquals(16, generated.length());
    }

    @Test
    @DisplayName("空白 traceId 视为缺失，重新生成而非透传")
    void shouldGenerateTraceId_whenHeaderBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        assertNotNull(generated);
        assertEquals(16, generated.length());
        assertNotEquals("   ", generated);
    }

    @Test
    @DisplayName("请求处理期间 MDC 可读到 traceId，处理结束后清空")
    void shouldExposeTraceIdInMdc_duringRequestOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "mdc-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        StringBuilder seenInsideChain = new StringBuilder();

        filter.doFilter(request, response,
                (req, res) -> seenInsideChain.append(MDC.get(TraceIdFilter.MDC_KEY)));

        assertEquals("mdc-1", seenInsideChain.toString());
        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "请求结束后 MDC 必须清空，避免线程复用时串号");
    }
}
