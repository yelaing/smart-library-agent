package com.library.agent.config;

import com.library.agent.entity.Book;
import com.library.agent.repository.BookRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;

    public DataInitializer(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            return;
        }
        bookRepository.save(new Book("9787111636996", "Spring实战", "Craig Walls", "在馆", "A区-3排-12号"));
        bookRepository.save(new Book("9787121429013", "深入理解Java虚拟机", "周志明", "已借出", "A区-1排-05号"));
        bookRepository.save(new Book("9787115580245", "TCP/IP详解", "Kevin Fall", "在馆", "B区-2排-08号"));
        bookRepository.save(new Book("9787115607348", "算法导论", "Thomas Cormen", "在馆", "B区-4排-01号"));
        bookRepository.save(new Book("9787115546081", "设计模式", "GoF", "已借出", "A区-2排-15号"));
        System.out.println("=== 已初始化 5 本馆藏图书 ===");
    }
}
