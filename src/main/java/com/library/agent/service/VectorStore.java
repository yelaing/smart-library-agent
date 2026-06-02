package com.library.agent.service;

import com.library.agent.entity.Book;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    private final Map<Long, double[]> store = new ConcurrentHashMap<>();

    public void put(Book book, double[] vector) {
        store.put(book.getId(), vector);
        log.debug("向量已存储: bookId={}", book.getId());
    }

    public List<Long> search(double[] queryVector, int topK) {
        return store.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .toList();
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }
}
