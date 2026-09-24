package com.library.agent.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.entity.BorrowRecord;
import com.library.agent.repository.BookRepository;
import com.library.agent.repository.BorrowRecordRepository;
import com.library.agent.service.RecommendationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 借还书事务的真实装配验证。
 *
 * <p>{@code LibraryToolTest} 里的 {@code TransactionTemplate} 是 mock，只能验证调用逻辑，
 * 证明不了真实的编程式事务与 JPA 装配是否可用。这里用容器里的真实 Bean 走一遍完整链路。</p>
 *
 * <p>加 {@code @Transactional} 让每个用例结束后回滚，避免污染共享的内存库
 * （种子数据里 {@code 9787111636996} 是"在馆"，被本测试借走会让其他测试的结果依赖执行顺序）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("借还书事务集成测试")
class LibraryToolTransactionTest {

    private static final String AVAILABLE_ISBN = "9787111636996";

    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BorrowRecordRepository borrowRecordRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RecommendationService recommendationService;

    private LibraryTool libraryTool;

    @BeforeEach
    void setUp() {
        libraryTool = new LibraryTool(bookRepository, borrowRecordRepository, transactionTemplate,
                recommendationService);
    }

    @Test
    @DisplayName("借书真实落库：状态变为已借出并生成借阅记录")
    void borrowBook_shouldPersistStatusAndBorrowRecord() {
        Book before = bookRepository.findByIsbn(AVAILABLE_ISBN).orElseThrow();
        assertEquals(BookStatus.AVAILABLE, before.getStatus());

        String result = libraryTool.borrowBook(AVAILABLE_ISBN, "集成测试读者");

        assertTrue(result.contains("借阅成功"));
        Book after = bookRepository.findByIsbn(AVAILABLE_ISBN).orElseThrow();
        assertEquals(BookStatus.BORROWED, after.getStatus());
        assertTrue(
                borrowRecordRepository.findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc(after.getId()).isPresent(),
                "借书后应存在未归还的借阅记录");
    }

    @Test
    @DisplayName("借书 → 还书完整链路：状态恢复且归还时间写入记录")
    void borrowThenReturn_shouldRestoreStatusAndWriteReturnDate() {
        libraryTool.borrowBook(AVAILABLE_ISBN, "集成测试读者");

        String result = libraryTool.returnBook(AVAILABLE_ISBN);

        assertTrue(result.contains("归还成功"));
        Book after = bookRepository.findByIsbn(AVAILABLE_ISBN).orElseThrow();
        assertEquals(BookStatus.AVAILABLE, after.getStatus());
        assertTrue(
                borrowRecordRepository.findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc(after.getId()).isEmpty(),
                "归还后不应还存在未归还的借阅记录（说明 return_date 已写入）");
        List<BorrowRecord> records = borrowRecordRepository.findAll();
        assertTrue(records.stream().anyMatch(r -> r.getReturnDate() != null),
                "应存在带归还时间的借阅记录");
    }

    @Test
    @DisplayName("已借出的图书再次借阅被拒绝，且不产生重复记录")
    void borrowBook_shouldReject_whenAlreadyBorrowed() {
        libraryTool.borrowBook(AVAILABLE_ISBN, "第一位读者");
        long recordsAfterFirstBorrow = borrowRecordRepository.count();

        String result = libraryTool.borrowBook(AVAILABLE_ISBN, "第二位读者");

        assertTrue(result.contains("已被借出"));
        assertEquals(recordsAfterFirstBorrow, borrowRecordRepository.count(),
                "被拒绝的借阅不应写入记录");
    }

    @Test
    @DisplayName("在馆图书直接归还被拒绝")
    void returnBook_shouldReject_whenNotBorrowed() {
        String result = libraryTool.returnBook(AVAILABLE_ISBN);

        assertTrue(result.contains("无需归还"));
    }
}
