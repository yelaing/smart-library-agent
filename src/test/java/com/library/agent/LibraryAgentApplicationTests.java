package com.library.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.library.agent.config.AgentConfig;
import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("应用集成测试")
class LibraryAgentApplicationTests {

    @Autowired
    private AgentConfig agentConfig;

    @Autowired
    private BookRepository bookRepository;

    @Test
    @DisplayName("Spring 容器正常启动")
    void contextLoads() {
    }

    @Test
    @DisplayName("Agent 配置 Bean 正确创建")
    void agentConfig_shouldCreateBeans() {
        assertNotNull(agentConfig);
    }

    @Test
    @DisplayName("种子数据应包含至少 5 本图书")
    void seedData_shouldHaveFiveBooks() {
        long count = bookRepository.count();
        assertTrue(count >= 5, "应有至少 5 本种子图书");
    }

    @Test
    @DisplayName("种子数据包含 Spring实战 且状态为在馆")
    void seedData_shouldHaveSpringBook() {
        Book book = bookRepository.findByIsbn("9787111636996").orElse(null);
        assertNotNull(book);
        assertEquals("Spring实战", book.getTitle());
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
    }
}
