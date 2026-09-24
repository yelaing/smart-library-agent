package com.library.agent.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 外部 HTTP 调用的 RestTemplate 配置。
 *
 * <p>必须显式设置超时：底层 {@code SimpleClientHttpRequestFactory} 默认无读超时，
 * 上游 Embedding 接口一旦挂住，调用线程会无限期阻塞。</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate embeddingRestTemplate(
            @Value("${library.embedding.connect-timeout:5s}") Duration connectTimeout,
            @Value("${library.embedding.read-timeout:15s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
