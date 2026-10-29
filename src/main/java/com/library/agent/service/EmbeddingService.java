package com.library.agent.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final String SILICONFLOW_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String DASHSCOPE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    /** 两家服务商的单次批量上限都是 10 条，2026-10 实测：11 条起返回 400。 */
    public static final int MAX_BATCH_SIZE = 10;

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final RestTemplate restTemplate;

    public EmbeddingService(
            @Value("${siliconflow.api-key:#{null}}") String siliconflowKey,
            @Value("${agentscope.dashscope.api-key}") String dashscopeKey,
            @Value("${library.embedding.base-url:}") String baseUrl,
            RestTemplate embeddingRestTemplate) {
        this.restTemplate = embeddingRestTemplate;
        if (siliconflowKey != null && !siliconflowKey.isBlank()) {
            this.apiKey = siliconflowKey;
            this.endpoint = SILICONFLOW_URL;
            this.model = "BAAI/bge-large-zh-v1.5";
            log.info("使用硅基流动 Embedding 服务");
        } else {
            this.apiKey = dashscopeKey;
            this.endpoint = baseUrl != null && !baseUrl.isBlank() ? baseUrl : DASHSCOPE_URL;
            this.model = "text-embedding-v4";
            if (this.endpoint.equals(DASHSCOPE_URL)) {
                log.info("使用百炼 DashScope Embedding 服务");
            } else {
                log.warn("Embedding 指向自定义地址: {}（仅用于本地压测，生产环境不应设置）", this.endpoint);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public double[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量向量化。DashScope / 硅基流动的单次请求上限都是 10 条，
     * 超过会被服务端以 400 拒绝，因此在本地先拦住并给出可读错误。
     */
    @SuppressWarnings("unchecked")
    public List<double[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次向量化条数不能超过 " + MAX_BATCH_SIZE + "，实际 " + texts.size());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
        );

        long start = System.currentTimeMillis();
        try {
            var response = restTemplate.postForObject(endpoint, new HttpEntity<>(body, headers), Map.class);
            if (response == null) {
                throw new RuntimeException("Embedding API 返回为空");
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.size() != texts.size()) {
                throw new RuntimeException("Embedding API 返回条数与请求不符: 期望 " + texts.size()
                        + "，实际 " + (data == null ? 0 : data.size()));
            }
            // 服务端不保证返回顺序，按 index 归位
            double[][] ordered = new double[texts.size()][];
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> item = data.get(i);
                int index = item.get("index") instanceof Number n ? n.intValue() : i;
                List<Double> raw = (List<Double>) item.get("embedding");
                ordered[index] = raw.stream().mapToDouble(Double::doubleValue).toArray();
            }
            log.debug("批量向量化完成: model={}, 条数={}, 维度={}, cost={}ms", model, texts.size(),
                    ordered[0].length, System.currentTimeMillis() - start);
            return List.of(ordered);
        } catch (Exception e) {
            log.error("批量向量化失败: endpoint={}, 条数={}, cost={}ms, reason={}", endpoint, texts.size(),
                    System.currentTimeMillis() - start, e.getMessage());
            throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
        }
    }
}
