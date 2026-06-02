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
        bookRepository.save(new Book("9787111636996", "Spring实战", "Craig Walls",
                BookStatus.AVAILABLE, "A区-3排-12号",
                "全面介绍Spring框架核心特性的实战指南，涵盖依赖注入、AOP、Spring MVC、Spring Boot等核心技术，适合Java后端开发者快速上手企业级应用开发"));
        bookRepository.save(new Book("9787121429013", "深入理解Java虚拟机", "周志明",
                BookStatus.BORROWED, "A区-1排-05号",
                "深入剖析JVM内部机制的经典之作，涵盖内存管理、类加载、垃圾回收、性能调优等内容，是Java开发者进阶必读的底层原理书籍"));
        bookRepository.save(new Book("9787115580245", "TCP/IP详解", "Kevin Fall",
                BookStatus.AVAILABLE, "B区-2排-08号",
                "计算机网络领域的权威著作，系统讲解TCP/IP协议族的工作原理，从链路层到应用层逐层剖析，适合想深入理解网络通信原理的读者"));
        bookRepository.save(new Book("9787115607348", "算法导论", "Thomas Cormen",
                BookStatus.AVAILABLE, "B区-4排-01号",
                "计算机科学领域公认的算法圣经，涵盖排序、图算法、动态规划、数据结构等核心主题，注重算法的正确性证明和复杂度分析，适合想打牢算法基础的学生"));
        bookRepository.save(new Book("9787115546081", "设计模式", "GoF",
                BookStatus.BORROWED, "A区-2排-15号",
                "面向对象设计的里程碑著作，详细讲解了23种经典设计模式及其应用场景，帮助开发者编写可复用、可维护的优雅代码，是软件工程必读经典"));
        log.info("已初始化 5 本馆藏图书");
    }
}
