package com.library.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class LibraryAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(LibraryAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LibraryAgentApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("智能图书馆助手已启动");
        log.info("API 接口: POST http://localhost:8080/v1/chat/completions");
        log.info("健康检查: POST http://localhost:8080/api/health");
    }
}
