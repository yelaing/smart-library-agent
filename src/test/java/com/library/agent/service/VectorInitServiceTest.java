package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("向量索引初始化测试")
class VectorInitServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private EmbeddingService embeddingService;

    private VectorStore vectorStore;
    private VectorInitService vectorInitService;

    @BeforeEach
    void setUp() {
        vectorStore = new VectorStore();
        vectorInitService = new VectorInitService(bookRepository, embeddingService, vectorStore);
    }

    @Test
    @DisplayName("为有简介的图书建立向量索引，跳过无简介的")
    void shouldIndexOnlyBooksWithDescription() {
        when(bookRepository.findAll()).thenReturn(List.of(
                book(1L, "有简介", "一本讲并发编程的书"),
                book(2L, "无简介", null)));
        when(embeddingService.embed("一本讲并发编程的书")).thenReturn(new double[]{1, 0});

        vectorInitService.initVectors();

        assertEquals(1, vectorStore.size());
        verify(embeddingService, never()).embed(null);
    }

    @Test
    @DisplayName("空白简介同样跳过，不调用向量化接口")
    void shouldSkipBlankDescription() {
        when(bookRepository.findAll()).thenReturn(List.of(book(1L, "空白简介", "   ")));

        vectorInitService.initVectors();

        assertEquals(0, vectorStore.size());
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("没有图书时直接返回")
    void shouldSkip_whenNoBooks() {
        when(bookRepository.findAll()).thenReturn(List.of());

        vectorInitService.initVectors();

        assertEquals(0, vectorStore.size());
        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("单本向量化失败不影响其余图书建索引")
    void shouldContinueIndexing_whenOneBookFails() {
        when(bookRepository.findAll()).thenReturn(List.of(
                book(1L, "会失败", "这本书向量化会报错"),
                book(2L, "会成功", "这本书能正常向量化")));
        when(embeddingService.embed("这本书向量化会报错")).thenThrow(new RuntimeException("401 Unauthorized"));
        when(embeddingService.embed("这本书能正常向量化")).thenReturn(new double[]{0, 1});

        vectorInitService.initVectors();

        assertEquals(1, vectorStore.size(), "一本失败不应中断整体索引构建");
    }

    private Book book(Long id, String title, String description) {
        Book book = new Book("isbn-" + id, title, "作者", BookStatus.AVAILABLE, "A区", description);
        book.setId(id);
        return book;
    }
}
