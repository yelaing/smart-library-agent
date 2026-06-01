package com.library.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.library.agent.config.AgentConfig;
import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LibraryAgentApplicationTests {

    @Autowired
    private AgentConfig agentConfig;

    @Autowired
    private BookRepository bookRepository;

    @Test
    void contextLoads() {
        // 验证 Spring 容器正常启动
    }

    @Test
    void agentConfig_shouldCreateBeans() {
        assertNotNull(agentConfig);
    }

    @Test
    void seedData_shouldHaveFiveBooks() {
        long count = bookRepository.count();
        assertTrue(count >= 5, "应有至少 5 本种子图书");
    }

    @Test
    void seedData_shouldHaveSpringBook() {
        Book book = bookRepository.findByIsbn("9787111636996").orElse(null);
        assertNotNull(book);
        assertEquals("Spring实战", book.getTitle());
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
    }
}
