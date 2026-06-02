package com.library.agent.tools;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.entity.BorrowRecord;
import com.library.agent.repository.BookRepository;
import com.library.agent.repository.BorrowRecordRepository;
import com.library.agent.service.EmbeddingService;
import com.library.agent.service.VectorStore;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

public class LibraryTool {

    private static final Logger log = LoggerFactory.getLogger(LibraryTool.class);

    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final TransactionTemplate transactionTemplate;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public LibraryTool(BookRepository bookRepository,
                       BorrowRecordRepository borrowRecordRepository,
                       TransactionTemplate transactionTemplate,
                       EmbeddingService embeddingService,
                       VectorStore vectorStore) {
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.transactionTemplate = transactionTemplate;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Tool(name = "search_book", description = "按书名关键词搜索图书，返回匹配的图书列表及其库存状态")
    public String searchBook(
            @ToolParam(name = "keyword", description = "书名关键词，支持模糊匹配") String keyword) {
        var books = bookRepository.findByTitleContaining(keyword);
        if (books.isEmpty()) {
            return "未找到包含「" + keyword + "」的图书";
        }
        StringBuilder sb = new StringBuilder("【搜索结果】\n");
        for (Book book : books) {
            sb.append(formatBook(book)).append("\n");
        }
        return sb.toString();
    }

    @Tool(name = "query_stock", description = "通过ISBN精确查询某本书的库存状态和位置")
    public String queryStock(@ToolParam(name = "isbn", description = "图书ISBN编号") String isbn) {
        return bookRepository.findByIsbn(isbn)
                .map(book -> String.format("《%s》- 状态：%s | 位置：%s", book.getTitle(), book.getStatus().getLabel(), book.getLocation()))
                .orElse("未找到ISBN为 " + isbn + " 的图书");
    }

    @Tool(name = "borrow_book", description = "借阅图书。需要提供ISBN和借阅人姓名")
    public String borrowBook(
            @ToolParam(name = "isbn", description = "图书ISBN编号") String isbn,
            @ToolParam(name = "borrower", description = "借阅人姓名") String borrower) {
        return transactionTemplate.execute(status -> {
            Book book = bookRepository.findByIsbn(isbn).orElse(null);
            if (book == null) {
                return "借阅失败：未找到ISBN为 " + isbn + " 的图书";
            }
            if (book.getStatus() == BookStatus.BORROWED) {
                return "借阅失败：《" + book.getTitle() + "》已被借出，暂时无法借阅。建议搜索同类书籍。";
            }
            book.setStatus(BookStatus.BORROWED);
            bookRepository.save(book);
            borrowRecordRepository.save(new BorrowRecord(book, borrower));
            log.info("借阅: {} → ISBN={} 《{}》", borrower, isbn, book.getTitle());
            return "借阅成功！《" + book.getTitle() + "》已登记到「" + borrower + "」名下，请于30天内归还。";
        });
    }

    @Tool(name = "return_book", description = "归还已借出的图书")
    public String returnBook(@ToolParam(name = "isbn", description = "图书ISBN编号") String isbn) {
        return transactionTemplate.execute(status -> {
            Book book = bookRepository.findByIsbn(isbn).orElse(null);
            if (book == null) {
                return "归还失败：未找到ISBN为 " + isbn + " 的图书";
            }
            if (book.getStatus() != BookStatus.BORROWED) {
                return "归还失败：《" + book.getTitle() + "》当前状态为「" + book.getStatus().getLabel() + "」，无需归还。";
            }
            book.setStatus(BookStatus.AVAILABLE);
            bookRepository.save(book);

            borrowRecordRepository.findTopByBookIdAndReturnDateIsNullOrderByBorrowDateDesc(book.getId())
                    .ifPresent(record -> {
                        record.setReturnDate(java.time.LocalDateTime.now());
                        borrowRecordRepository.save(record);
                    });

            log.info("归还: ISBN={} 《{}》", isbn, book.getTitle());
            return "归还成功！《" + book.getTitle() + "》已归还入库。";
        });
    }

    @Tool(name = "recommend_book", description = "根据用户的模糊需求推荐图书。用户可以说想看某类主题或风格的书，Agent 会通过语义检索匹配最相关的馆藏图书。优先使用此工具处理推荐类请求")
    public String recommendBook(
            @ToolParam(name = "query", description = "用户的阅读需求描述，如'想学并发编程'、'对设计模式感兴趣'") String query) {
        if (vectorStore.size() == 0) {
            return "推荐服务暂不可用，向量索引为空。请确认图书数据已初始化。";
        }
        try {
            double[] queryVector = embeddingService.embed(query);
            var bookIds = vectorStore.search(queryVector, 3);
            if (bookIds.isEmpty()) {
                return "抱歉，没有找到与「" + query + "」相关的馆藏图书。";
            }
            StringBuilder sb = new StringBuilder("【智能推荐 - 语义检索结果】\n");
            for (Long id : bookIds) {
                bookRepository.findById(id).ifPresent(book -> sb.append(formatBook(book)).append("\n"));
            }
            sb.append("\n如需借阅，请告诉我 ISBN 编号和借阅人姓名。");
            return sb.toString();
        } catch (Exception e) {
            log.error("语义推荐失败: {}", e.getMessage());
            return "推荐服务暂时不可用，请稍后重试或使用 search_book 按书名关键词搜索。";
        }
    }

    private String formatBook(Book book) {
        return String.format("[%s] 《%s》- %s | 状态：%s | 位置：%s",
                book.getIsbn(), book.getTitle(), book.getAuthor(), book.getStatus().getLabel(), book.getLocation());
    }
}
