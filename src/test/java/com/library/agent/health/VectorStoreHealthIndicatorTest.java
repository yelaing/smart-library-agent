package com.library.agent.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.service.VectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@DisplayName("向量索引健康指示测试")
class VectorStoreHealthIndicatorTest {

    private final VectorStore vectorStore = new VectorStore();
    private final VectorStoreHealthIndicator indicator = new VectorStoreHealthIndicator(vectorStore);

    @Test
    @DisplayName("索引为空时上报 UNKNOWN 而非 DOWN")
    void shouldReportUnknown_whenIndexEmpty() {
        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals(0, health.getDetails().get("indexedBooks"));
    }

    @Test
    @DisplayName("索引就绪时上报 UP 并带上索引数量")
    void shouldReportUp_whenIndexReady() {
        Book book = new Book("isbn-1", "书名", "作者", BookStatus.AVAILABLE, "A区");
        book.setId(1L);
        vectorStore.put(book, new double[]{1, 0});

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("indexedBooks"));
    }
}
