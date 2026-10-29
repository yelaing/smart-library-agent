package com.library.agent.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.agent.entity.Book;
import com.library.agent.repository.BookRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

@ExtendWith(MockitoExtension.class)
@DisplayName("种子书目初始化测试")
class DataInitializerTest {

    @Mock
    private BookRepository bookRepository;

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    @Test
    @DisplayName("库中已有数据时跳过导入，避免每次重启重复写入")
    void shouldSkip_whenRepositoryNotEmpty() {
        when(bookRepository.count()).thenReturn(5L);

        newInitializer().run();

        verify(bookRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("空库时从 books.json 读入完整书目")
    void shouldLoadSeedBooks_whenRepositoryEmpty() {
        when(bookRepository.count()).thenReturn(0L);

        newInitializer().run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Book>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookRepository).saveAll(captor.capture());
        assertTrue(captor.getValue().size() >= 50,
                "books.json 应至少包含 50 本馆藏，实际 " + captor.getValue().size());
    }

    private DataInitializer newInitializer() {
        return new DataInitializer(bookRepository, resourceLoader, "classpath:books.json");
    }
}
