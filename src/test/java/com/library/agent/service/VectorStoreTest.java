package com.library.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("向量索引与余弦相似度测试")
class VectorStoreTest {

    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new VectorStore();
    }

    @Test
    @DisplayName("按余弦相似度降序返回 TopK，而非插入顺序")
    void search_shouldRankByCosineSimilarityDescending() {
        vectorStore.put(book(1L), new double[]{1, 0});    // 与 query 同向，相似度 1.0
        vectorStore.put(book(2L), new double[]{0, 1});    // 与 query 正交，相似度 0
        vectorStore.put(book(3L), new double[]{0.9, 0.1}); // 接近同向，相似度 ≈0.994

        List<Long> top2 = vectorStore.search(new double[]{1, 0}, 2);

        assertEquals(List.of(1L, 3L), top2);
    }

    @Test
    @DisplayName("相似度与向量模长无关：长向量不应仅因模长大而排前")
    void search_shouldBeIndependentOfVectorMagnitude() {
        vectorStore.put(book(1L), new double[]{1, 0});     // 余弦 1.0，点积 1
        vectorStore.put(book(2L), new double[]{100, 1});   // 余弦 ≈0.99995，点积 100

        List<Long> top1 = vectorStore.search(new double[]{1, 0}, 1);

        // 若退化成点积，book2 会因模长大而排到第一
        assertEquals(List.of(1L), top1);
    }

    @Test
    @DisplayName("topK 大于库存量时返回全部")
    void search_shouldReturnAll_whenTopKExceedsSize() {
        vectorStore.put(book(1L), new double[]{1, 0});
        vectorStore.put(book(2L), new double[]{0, 1});

        assertEquals(2, vectorStore.search(new double[]{1, 0}, 10).size());
    }

    @Test
    @DisplayName("零向量相似度按 0 处理，不出现除零")
    void search_shouldTreatZeroVectorAsZeroSimilarity() {
        vectorStore.put(book(1L), new double[]{0, 0});

        assertEquals(List.of(1L), vectorStore.search(new double[]{1, 0}, 1));
    }

    @Test
    @DisplayName("空索引返回空列表")
    void search_shouldReturnEmpty_whenStoreIsEmpty() {
        assertTrue(vectorStore.search(new double[]{1, 0}, 3).isEmpty());
    }

    @Test
    @DisplayName("同一本书重复写入时覆盖旧向量而非新增")
    void put_shouldOverwriteVectorForSameBook() {
        Book book1 = book(1L);
        vectorStore.put(book1, new double[]{1, 0});      // 与 query {1,0} 相似度 1.0
        vectorStore.put(book(2L), new double[]{0.5, 0.5}); // 相似度 ≈0.707

        List<Long> beforeOverwrite = vectorStore.search(new double[]{1, 0}, 1);
        vectorStore.put(book1, new double[]{0, 1});      // 覆盖为与 query 正交

        List<Long> afterOverwrite = vectorStore.search(new double[]{1, 0}, 1);

        assertEquals(2, vectorStore.size(), "覆盖写入不应新增条目");
        assertEquals(List.of(1L), beforeOverwrite);
        // 若旧向量 {1,0} 仍残留，book1 会以 1.0 继续排第一
        assertEquals(List.of(2L), afterOverwrite);
    }

    @Test
    @DisplayName("clear 清空索引")
    void clear_shouldEmptyStore() {
        vectorStore.put(book(1L), new double[]{1, 0});

        vectorStore.clear();

        assertEquals(0, vectorStore.size());
    }

    private Book book(Long id) {
        Book book = new Book("isbn-" + id, "书名" + id, "作者", BookStatus.AVAILABLE, "A区");
        book.setId(id);
        return book;
    }
}
