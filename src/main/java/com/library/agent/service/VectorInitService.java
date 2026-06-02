package com.library.agent.service;

import com.library.agent.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
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

    @EventListener(ApplicationReadyEvent.class)
    public void initVectors() {
        var books = bookRepository.findAll();
        if (books.isEmpty()) {
            return;
        }
        for (var book : books) {
            if (book.getDescription() == null || book.getDescription().isBlank()) {
                continue;
            }
            try {
                double[] vector = embeddingService.embed(book.getDescription());
                vectorStore.put(book, vector);
                log.info("向量已索引: 《{}》", book.getTitle());
            } catch (Exception e) {
                log.warn("向量化失败 《{}》: {}", book.getTitle(), e.getMessage());
            }
        }
        log.info("向量索引构建完成，共 {} 本图书", vectorStore.size());
    }
}
