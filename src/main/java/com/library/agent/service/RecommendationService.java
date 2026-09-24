package com.library.agent.service;

import com.library.agent.entity.Book;
import com.library.agent.repository.BookRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 图书语义推荐（RAG 检索）。
 *
 * <p>独立成 Spring Bean 而不是留在 LibraryTool 内，有两个原因：
 * LibraryTool 由 AgentConfig 手工 {@code new} 出来、并非容器管理的 Bean，
 * 基于 AOP 的 {@code @Cacheable} 对它不会生效；且业务逻辑与 LLM 参数适配混在一起无法单测。</p>
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int TOP_K = 3;

    private final BookRepository bookRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public RecommendationService(BookRepository bookRepository, EmbeddingService embeddingService,
                                 VectorStore vectorStore) {
        this.bookRepository = bookRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 向量索引是否已构建完成。未就绪时调用方应提示服务不可用，而不是把"无结果"当成"没匹配到"。
     */
    public boolean isReady() {
        return vectorStore.size() > 0;
    }

    /**
     * 按语义检索最相关的馆藏图书。
     *
     * <p>缓存策略：以 query 文本为 key。空结果不缓存（{@code unless}），
     * 向量化失败时异常向上抛出、同样不会落缓存 —— 避免把一次瞬时故障固化成缓存条目。</p>
     */
    @Cacheable(cacheNames = "bookRecommendation", key = "#query",
            unless = "#result == null || #result.isEmpty()")
    public List<Book> search(String query) {
        if (!isReady()) {
            log.warn("向量索引为空，跳过语义推荐: query={}", query);
            return List.of();
        }
        long start = System.currentTimeMillis();
        double[] queryVector = embeddingService.embed(query);
        List<Book> books = vectorStore.search(queryVector, TOP_K).stream()
                .map(bookRepository::findById)
                .flatMap(Optional::stream)
                .toList();
        log.info("语义推荐完成: query={}, hits={}, cost={}ms", query, books.size(),
                System.currentTimeMillis() - start);
        return books;
    }
}
