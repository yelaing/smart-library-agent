package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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

    private static final double MIN_SCORE = 0.3;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(bookRepository, embeddingService, vectorStore, MIN_SCORE);
    }

    @Test
    @DisplayName("索引就绪时按向量检索顺序返回图书")
    void shouldReturnBooksInVectorOrder() {
        Book spring = book(1L, "Spring实战");
        Book algorithm = book(2L, "算法导论");
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("想学Java")).thenReturn(new double[]{1, 0});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(
                new VectorStore.ScoredBook(1L, 0.9),
                new VectorStore.ScoredBook(2L, 0.6)));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(spring));
        when(bookRepository.findById(2L)).thenReturn(Optional.of(algorithm));

        List<Book> result = service.search("想学Java");

        assertEquals(List.of(spring, algorithm), result);
    }

    @Test
    @DisplayName("相似度低于阈值的命中被丢弃，不拿无关图书凑满 topK")
    void shouldDropHitsBelowMinScore() {
        Book spring = book(1L, "Spring实战");
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("想学Java")).thenReturn(new double[]{1, 0});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(
                new VectorStore.ScoredBook(1L, 0.8),
                new VectorStore.ScoredBook(2L, 0.1)));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(spring));

        List<Book> result = service.search("想学Java");

        assertEquals(List.of(spring), result);
        verify(bookRepository, never()).findById(2L);
    }

    @Test
    @DisplayName("全部命中都低于阈值时返回空列表，让上层如实告知没有相关馆藏")
    void shouldReturnEmpty_whenAllHitsBelowMinScore() {
        when(vectorStore.size()).thenReturn(2);
        when(embeddingService.embed("怎么做红烧肉")).thenReturn(new double[]{1, 0});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(
                new VectorStore.ScoredBook(1L, 0.2),
                new VectorStore.ScoredBook(2L, 0.1)));
        // 这两本确实在库里：断言"即使回表能查到，也因分数不够而必须返回空"，
        // 否则去掉阈值过滤后测试依然会通过，失去鉴别力
        lenient().when(bookRepository.findById(1L)).thenReturn(Optional.of(book(1L, "Spring实战")));
        lenient().when(bookRepository.findById(2L)).thenReturn(Optional.of(book(2L, "算法导论")));

        List<Book> result = service.search("怎么做红烧肉");

        assertTrue(result.isEmpty());
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
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(
                new VectorStore.ScoredBook(1L, 0.9),
                new VectorStore.ScoredBook(99L, 0.8)));
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
