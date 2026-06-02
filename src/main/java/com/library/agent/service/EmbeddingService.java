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
    private static final String EMBEDDING_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public EmbeddingService(@Value("${agentscope.dashscope.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
    }

    @SuppressWarnings("unchecked")
    public double[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", "text-embedding-v4",
                "input", text,
                "dimensions", 1024
        );

        try {
            var response = restTemplate.postForObject(EMBEDDING_URL, new HttpEntity<>(body, headers), Map.class);
            if (response == null) {
                throw new RuntimeException("Embedding API 返回为空");
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            List<Double> raw = (List<Double>) data.get(0).get("embedding");
            return raw.stream().mapToDouble(Double::doubleValue).toArray();
        } catch (Exception e) {
            log.error("文本向量化失败: {}", e.getMessage());
            throw new RuntimeException("文本向量化失败: " + e.getMessage(), e);
        }
    }
}
