package com.library.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.repository.BookRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 启动时用 {@code classpath:books.json} 初始化馆藏种子数据。
 *
 * <p>书目外置成数据文件而不是硬编码在代码里：交付到客户现场时直接替换这份文件即可，
 * 不需要改代码重新打包。</p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final BookRepository bookRepository;
    private final ResourceLoader resourceLoader;
    private final String seedLocation;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataInitializer(BookRepository bookRepository, ResourceLoader resourceLoader,
                           @Value("${library.seed.file:classpath:books.json}") String seedLocation) {
        this.bookRepository = bookRepository;
        this.resourceLoader = resourceLoader;
        this.seedLocation = seedLocation;
    }

    @Override
    public void run(String... args) {
        if (bookRepository.count() > 0) {
            return;
        }
        List<Book> books = loadSeedBooks();
        bookRepository.saveAll(books);
        log.info("已初始化 {} 本馆藏图书", books.size());
    }

    private List<Book> loadSeedBooks() {
        Resource resource = resourceLoader.getResource(seedLocation);
        try (InputStream in = resource.getInputStream()) {
            SeedBook[] seeds = objectMapper.readValue(in, SeedBook[].class);
            return Arrays.stream(seeds).map(SeedBook::toEntity).toList();
        } catch (IOException e) {
            // 种子数据缺失或格式错误属于部署问题，直接抛出比带着空库启动更容易排查
            throw new IllegalStateException("读取种子书目失败: " + seedLocation, e);
        }
    }

    /** books.json 中的一行书目。 */
    record SeedBook(String isbn, String title, String author, String status,
                    String location, String description) {

        Book toEntity() {
            return new Book(isbn, title, author, BookStatus.valueOf(status), location, description);
        }
    }
}
