package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@DisplayName("文本向量化服务测试")
class EmbeddingServiceTest {

    private static final String SILICONFLOW_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    private final RestTemplate restTemplate = mock(RestTemplate.class);

    @Test
    @DisplayName("未配置硅基流动 key 时走百炼 DashScope 接口")
    void shouldUseDashScope_whenSiliconflowKeyAbsent() {
        stubEmbeddingResponse(0.1, 0.2);
        EmbeddingService service = new EmbeddingService("", "dashscope-key", "", restTemplate);

        double[] vector = service.embed("并发编程");

        assertArrayEquals(new double[]{0.1, 0.2}, vector, 1e-9);
        verify(restTemplate).postForObject(eq(DASHSCOPE_URL), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("配置硅基流动 key 时切换到 BGE 中文模型")
    void shouldUseSiliconflowBgeModel_whenKeyConfigured() {
        stubEmbeddingResponse(1.0, 2.0);
        EmbeddingService service = new EmbeddingService("sk-siliconflow", "dashscope-key", "", restTemplate);

        service.embed("并发编程");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq(SILICONFLOW_URL), captor.capture(), eq(Map.class));
        assertEquals("BAAI/bge-large-zh-v1.5", captor.getValue().getBody().get("model"));
        assertEquals(List.of("并发编程"), captor.getValue().getBody().get("input"),
                "单条也走批量接口，input 必须是数组而非字符串");
    }

    @Test
    @DisplayName("配置 base-url 时把 Embedding 请求打到自定义地址（压测指向本地 mock）")
    void shouldUseCustomBaseUrl_whenConfigured() {
        stubEmbeddingResponse(0.3);
        EmbeddingService service = new EmbeddingService("", "dashscope-key",
                "http://127.0.0.1:18081/compatible-mode/v1/embeddings", restTemplate);

        service.embed("并发编程");

        verify(restTemplate).postForObject(
                eq("http://127.0.0.1:18081/compatible-mode/v1/embeddings"),
                any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("批量超过服务商上限 10 条时在本地拒绝，不发出请求")
    void shouldRejectBatchLargerThanTen() {
        EmbeddingService service = new EmbeddingService("", "key", "", restTemplate);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.embedBatch(java.util.Collections.nCopies(11, "x")));

        assertTrue(error.getMessage().contains("不能超过 10"));
        verify(restTemplate, org.mockito.Mockito.never())
                .postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    @DisplayName("批量结果按响应里的 index 归位，不依赖数组顺序")
    void shouldOrderBatchResultByIndex() {
        List<Double> first = List.of(1.0, 0.0);
        List<Double> second = List.of(0.0, 1.0);
        // 服务端故意倒序返回
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("data", List.of(
                        Map.of("index", 1, "embedding", second),
                        Map.of("index", 0, "embedding", first))));
        EmbeddingService service = new EmbeddingService("", "key", "", restTemplate);

        List<double[]> vectors = service.embedBatch(List.of("甲", "乙"));

        assertArrayEquals(new double[]{1.0, 0.0}, vectors.get(0), 1e-9);
        assertArrayEquals(new double[]{0.0, 1.0}, vectors.get(1), 1e-9);
    }

    @Test
    @DisplayName("接口返回空响应体时抛出可读异常")
    void shouldThrow_whenResponseIsNull() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenReturn(null);
        EmbeddingService service = new EmbeddingService("", "key", "", restTemplate);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.embed("x"));

        assertTrue(error.getMessage().contains("返回为空"));
    }

    @Test
    @DisplayName("HTTP 调用失败时包装为文本向量化失败并保留原因")
    void shouldWrapRestClientException() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("401 Unauthorized"));
        EmbeddingService service = new EmbeddingService("", "key", "", restTemplate);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.embed("x"));

        assertTrue(error.getMessage().contains("文本向量化失败"));
        assertTrue(error.getCause() instanceof RestClientException);
    }

    @Test
    @DisplayName("响应结构缺失 embedding 字段时包装成可读异常，不泄漏裸 NPE")
    void shouldWrapMalformedPayload() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("data", List.of(Map.of("unexpected", "field"))));
        EmbeddingService service = new EmbeddingService("", "key", "", restTemplate);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.embed("x"));

        assertTrue(error.getMessage().contains("文本向量化失败"));
    }

    @SuppressWarnings("unchecked")
    private void stubEmbeddingResponse(double... values) {
        List<Double> embedding = new java.util.ArrayList<>();
        for (double value : values) {
            embedding.add(value);
        }
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("data", List.of(Map.of("embedding", embedding))));
    }
}
