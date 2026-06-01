package com.library.agent.config;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final BookRepository bookRepository;

    public DataInitializer(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            return;
        }
        bookRepository.save(new Book("9787111636996", "Spring实战", "Craig Walls", BookStatus.AVAILABLE, "A区-3排-12号"));
        bookRepository.save(new Book("9787121429013", "深入理解Java虚拟机", "周志明", BookStatus.BORROWED, "A区-1排-05号"));
        bookRepository.save(new Book("9787115580245", "TCP/IP详解", "Kevin Fall", BookStatus.AVAILABLE, "B区-2排-08号"));
        bookRepository.save(new Book("9787115607348", "算法导论", "Thomas Cormen", BookStatus.AVAILABLE, "B区-4排-01号"));
        bookRepository.save(new Book("9787115546081", "设计模式", "GoF", BookStatus.BORROWED, "A区-2排-15号"));
        log.info("已初始化 5 本馆藏图书");
    }
}
