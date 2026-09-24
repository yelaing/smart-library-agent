package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("语义推荐服务测试")
class RecommendationServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(bookRepository, embeddingService, vectorStore);
    }

    @Test
    @DisplayName("索引就绪时按向量检索顺序返回图书")
    void shouldReturnBooksInVectorOrder() {
        Book spring = book(1L, "Spring实战");
        Book algorithm = book(2L, "算法导论");
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("想学Java")).thenReturn(new double[]{1, 0});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(1L, 2L));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(spring));
        when(bookRepository.findById(2L)).thenReturn(Optional.of(algorithm));

        List<Book> result = service.search("想学Java");

        assertEquals(List.of(spring, algorithm), result);
    }

    @Test
    @DisplayName("索引为空时返回空列表，且不调用向量化接口")
    void shouldReturnEmpty_whenIndexIsEmpty() {
        when(vectorStore.size()).thenReturn(0);

        List<Book> result = service.search("想学Java");

        assertTrue(result.isEmpty());
        verify(embeddingService, never()).embed(any());
    }

    @Test
    @DisplayName("向量化失败时异常向上抛出，不吞掉")
    void shouldPropagate_whenEmbeddingFails() {
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("想学Java")).thenThrow(new RuntimeException("文本向量化失败: 401"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.search("想学Java"));

        assertTrue(error.getMessage().contains("文本向量化失败"));
    }

    @Test
    @DisplayName("向量命中了已删除的图书时跳过，不抛异常")
    void shouldSkipIdsThatNoLongerExist() {
        Book spring = book(1L, "Spring实战");
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("想学Java")).thenReturn(new double[]{1, 0});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(1L, 99L));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(spring));
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        List<Book> result = service.search("想学Java");

        assertEquals(List.of(spring), result);
    }

    @Test
    @DisplayName("isReady 反映向量索引是否已构建")
    void isReady_shouldReflectVectorStoreSize() {
        when(vectorStore.size()).thenReturn(0);
        assertFalse(service.isReady());

        when(vectorStore.size()).thenReturn(5);
        assertTrue(service.isReady());
    }

    private Book book(Long id, String title) {
        Book book = new Book("isbn-" + id, title, "作者", BookStatus.AVAILABLE, "A区");
        book.setId(id);
        return book;
    }
}
