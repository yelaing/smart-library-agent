package com.library.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能图书馆助手 - Spring Boot 启动类。
 */
@SpringBootApplication
public class LibraryAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryAgentApplication.class, args);
        System.out.println("\n=== 智能图书馆助手已启动 ===");
        System.out.println("API 接口: POST http://localhost:8080/v1/chat/completions");
        System.out.println("健康检查: GET  http://localhost:8080/api/health\n");
    }
}
