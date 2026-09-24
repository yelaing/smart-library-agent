package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.agent.entity.Book;
import com.library.agent.repository.BookRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 验证 {@code @Cacheable} 真的生效。
 *
 * <p>这是支撑"把推荐逻辑抽成独立 Spring Bean"这一架构决策的关键测试：
 * LibraryTool 由 AgentConfig 手工 new 出来、不经容器代理，缓存注解对它无效；
 * 改用容器管理的 {@link RecommendationService} 后缓存才会命中。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("推荐缓存测试")
class RecommendationCacheTest {

    private static final String CACHE_NAME = "bookRecommendation";

    @Autowired
    private RecommendationService recommendationService;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CacheManager cacheManager;
    @MockitoBean
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        resetSharedState();
        // 全部馆藏用同一向量入库，保证推荐必定有结果
        List<Book> books = bookRepository.findAll();
        for (Book book : books) {
            vectorStore.put(book, new double[]{1, 0});
        }
        when(embeddingService.embed(anyString())).thenReturn(new double[]{1, 0});
    }

    @AfterEach
    void tearDown() {
        resetSharedState();
    }

    @Test
    @DisplayName("同一 query 重复调用只向量化一次")
    void shouldHitCache_onRepeatedQuery() {
        List<Book> first = recommendationService.search("并发编程");
        List<Book> second = recommendationService.search("并发编程");

        assertFalse(first.isEmpty());
        assertEquals(first, second);
        verify(embeddingService, times(1)).embed("并发编程");
    }

    @Test
    @DisplayName("不同 query 不共享缓存条目")
    void shouldNotShareCache_betweenDifferentQueries() {
        List<Book> concurrent = recommendationService.search("并发编程");
        List<Book> dataStructure = recommendationService.search("数据结构");

        assertFalse(concurrent.isEmpty());
        assertFalse(dataStructure.isEmpty());
        verify(embeddingService, times(1)).embed("并发编程");
        verify(embeddingService, times(1)).embed("数据结构");
    }

    @Test
    @DisplayName("空结果不写入缓存，避免把'没匹配到'固化")
    void shouldNotCacheEmptyResult() {
        vectorStore.clear();

        assertTrue(recommendationService.search("没有匹配").isEmpty());
        assertNull(cache().get("没有匹配"), "空结果不应产生缓存条目");
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        assertTrue(cache != null, "缓存 " + CACHE_NAME + " 应已创建");
        return cache;
    }

    /** VectorStore 与 Cache 都是容器级单例，测试间必须复位，否则结果依赖执行顺序。 */
    private void resetSharedState() {
        cache().clear();
        vectorStore.clear();
    }
}
