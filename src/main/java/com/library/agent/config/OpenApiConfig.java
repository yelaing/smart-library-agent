package com.library.agent.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元信息。UI 地址与文档路径在 application.yml 的 springdoc 下配置。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("智能图书馆助手 API")
                .version("1.0.0")
                .description("""
                        基于 ReAct Agent 的图书馆助手。对话接口兼容 OpenAI Chat Completions 协议，
                        Agent 会按用户意图自主调度五个工具：search_book、query_stock、
                        borrow_book、return_book、recommend_book（语义推荐）。

                        健康检查与指标见 /actuator/health 与 /actuator/metrics。
                        """));
    }
}
