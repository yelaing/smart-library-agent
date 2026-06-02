package com.library.agent.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.entity.BorrowRecord;
import com.library.agent.repository.BookRepository;
import com.library.agent.repository.BorrowRecordRepository;
import com.library.agent.service.EmbeddingService;
import com.library.agent.service.VectorStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("图书工具测试")
class LibraryToolTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;

    private LibraryTool libraryTool;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        libraryTool = new LibraryTool(bookRepository, borrowRecordRepository, transactionTemplate,
                embeddingService, vectorStore);
    }

    @Test
    @DisplayName("搜索图书 - 关键词匹配时返回结果")
    void searchBook_shouldReturnResults_whenKeywordMatches() {
        when(bookRepository.findByTitleContaining("Spring")).thenReturn(List.of(
                new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.AVAILABLE, "A区-3排-12号")
        ));

        String result = libraryTool.searchBook("Spring");

        assertTrue(result.contains("Spring实战"));
        assertTrue(result.contains("A区-3排-12号"));
    }

    @Test
    @DisplayName("搜索图书 - 无匹配时返回提示")
    void searchBook_shouldReturnEmptyMessage_whenNoMatch() {
        when(bookRepository.findByTitleContaining("不存在")).thenReturn(List.of());

        String result = libraryTool.searchBook("不存在");

        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("查询库存 - ISBN 存在时返回图书信息")
    void queryStock_shouldReturnBookInfo_whenIsbnExists() {
        when(bookRepository.findByIsbn("9787111636996")).thenReturn(Optional.of(
                new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.AVAILABLE, "A区-3排-12号")
        ));

        String result = libraryTool.queryStock("9787111636996");

        assertTrue(result.contains("Spring实战"));
        assertTrue(result.contains("在馆"));
    }

    @Test
    @DisplayName("查询库存 - ISBN 不存在时返回未找到")
    void queryStock_shouldReturnNotFound_whenIsbnNotExists() {
        when(bookRepository.findByIsbn("000")).thenReturn(Optional.empty());

        String result = libraryTool.queryStock("000");

        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("借书 - 图书在馆时借阅成功")
    void borrowBook_shouldSucceed_whenBookAvailable() {
        Book book = new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.AVAILABLE, "A区-3排-12号");
        when(bookRepository.findByIsbn("9787111636996")).thenReturn(Optional.of(book));

        String result = libraryTool.borrowBook("9787111636996", "张三");

        assertTrue(result.contains("借阅成功"));
        assertEquals(BookStatus.BORROWED, book.getStatus());
        verify(bookRepository).save(book);
        verify(borrowRecordRepository).save(any(BorrowRecord.class));
    }

    @Test
    @DisplayName("借书 - 图书不存在时借阅失败")
    void borrowBook_shouldFail_whenBookNotFound() {
        when(bookRepository.findByIsbn("000")).thenReturn(Optional.empty());

        String result = libraryTool.borrowBook("000", "张三");

        assertTrue(result.contains("借阅失败"));
        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("借书 - 图书已借出时借阅失败")
    void borrowBook_shouldFail_whenAlreadyBorrowed() {
        Book book = new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.BORROWED, "A区-3排-12号");
        when(bookRepository.findByIsbn("9787111636996")).thenReturn(Optional.of(book));

        String result = libraryTool.borrowBook("9787111636996", "张三");

        assertTrue(result.contains("借阅失败"));
        assertTrue(result.contains("已被借出"));
    }

    @Test
    @DisplayName("还书 - 已借出图书归还成功")
    void returnBook_shouldSucceed_whenBookIsBorrowed() {
        Book book = new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.BORROWED, "A区-3排-12号");
        book.setId(1L);
        when(bookRepository.findByIsbn("9787111636996")).thenReturn(Optional.of(book));
        BorrowRecord record = new BorrowRecord(book, "张三");
        when(borrowRecordRepository.findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc(1L))
                .thenReturn(Optional.of(record));

        String result = libraryTool.returnBook("9787111636996");

        assertTrue(result.contains("归还成功"));
        assertEquals(BookStatus.AVAILABLE, book.getStatus());
        assertNotNull(record.getReturnDate());
    }

    @Test
    @DisplayName("还书 - 图书不存在时归还失败")
    void returnBook_shouldFail_whenBookNotFound() {
        when(bookRepository.findByIsbn("000")).thenReturn(Optional.empty());

        String result = libraryTool.returnBook("000");

        assertTrue(result.contains("归还失败"));
        assertTrue(result.contains("未找到"));
    }

    @Test
    @DisplayName("还书 - 图书未借出时归还失败")
    void returnBook_shouldFail_whenBookNotBorrowed() {
        Book book = new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.AVAILABLE, "A区-3排-12号");
        when(bookRepository.findByIsbn("9787111636996")).thenReturn(Optional.of(book));

        String result = libraryTool.returnBook("9787111636996");

        assertTrue(result.contains("归还失败"));
        assertTrue(result.contains("无需归还"));
    }

    @Test
    @DisplayName("语义推荐 - 根据用户需求检索匹配图书")
    void recommendBook_shouldReturnResults_whenVectorStoreHasMatch() {
        Book book = new Book("9787111636996", "Spring实战", "Craig Walls",
                BookStatus.AVAILABLE, "A区-3排-12号", "一本Spring框架实战书");
        book.setId(1L);
        when(vectorStore.size()).thenReturn(1);
        when(embeddingService.embed("想学Java框架")).thenReturn(new double[]{0.1, 0.2});
        when(vectorStore.search(any(double[].class), eq(3))).thenReturn(List.of(1L));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        String result = libraryTool.recommendBook("想学Java框架");

        assertTrue(result.contains("智能推荐"));
        assertTrue(result.contains("Spring实战"));
    }

    @Test
    @DisplayName("语义推荐 - 向量索引为空时返回提示")
    void recommendBook_shouldReturnUnavailable_whenVectorStoreEmpty() {
        when(vectorStore.size()).thenReturn(0);

        String result = libraryTool.recommendBook("想学Java");

        assertTrue(result.contains("暂不可用"));
    }
}
