package com.library.agent.service;

import com.library.agent.entity.Book;
import com.library.agent.repository.BookRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "library.vector-init.enabled", havingValue = "true", matchIfMissing = true)
public class VectorInitService {

    private static final Logger log = LoggerFactory.getLogger(VectorInitService.class);

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public VectorInitService(BookRepository bookRepository, EmbeddingService embeddingService, VectorStore vectorStore) {
        this.bookRepository = bookRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 全量构建向量索引。
     *
     * <p>按服务商上限 10 条一批调用：逐条调用时 5 万本需要 50000 次 HTTP 往返，
     * 批量后降到 5000 次，是同一份数据下索引构建耗时的主要差异来源。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initVectors() {
        var books = bookRepository.findAll();
        if (books.isEmpty()) {
            return;
        }
        long start = System.currentTimeMillis();
        List<Book> batch = new ArrayList<>(EmbeddingService.MAX_BATCH_SIZE);
        for (var book : books) {
            if (book.getDescription() == null || book.getDescription().isBlank()) {
                continue;
            }
            batch.add(book);
            if (batch.size() == EmbeddingService.MAX_BATCH_SIZE) {
                indexBatch(batch);
                batch.clear();
            }
        }
        indexBatch(batch);
        log.info("向量索引构建完成: 入选图书={}, 已索引={}, 耗时={}ms", books.size(), vectorStore.size(),
                System.currentTimeMillis() - start);
    }

    private void indexBatch(List<Book> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            List<double[]> vectors = embeddingService.embedBatch(
                    batch.stream().map(Book::getDescription).toList());
            for (int i = 0; i < batch.size(); i++) {
                vectorStore.put(batch.get(i), vectors.get(i));
            }
        } catch (Exception e) {
            // 整批失败后退化为逐条：批量是把 10 本书绑在一起，一本参数异常会拖垮整批
            log.warn("批量向量化失败，退化为逐条重试: 条数={}, reason={}", batch.size(), e.getMessage());
            for (Book book : batch) {
                try {
                    vectorStore.put(book, embeddingService.embed(book.getDescription()));
                } catch (Exception itemError) {
                    log.warn("向量化失败 《{}》: {}", book.getTitle(), itemError.getMessage());
                }
            }
        }
    }
}
