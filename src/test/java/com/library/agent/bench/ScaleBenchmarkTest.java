package com.library.agent.bench;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.agent.entity.Book;
import com.library.agent.entity.BookStatus;
import com.library.agent.service.EmbeddingService;
import com.library.agent.service.VectorStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestTemplate;

/**
 * 规模基准：一次嵌入 5 万条，在 5k / 10k / 50k 三个前缀上分别采样。
 *
 * <p>默认不跑（非单测）。执行方式：</p>
 * <pre>
 * DASHSCOPE_API_KEY=sk-xxx mvn -o test -Dbench=true -Dtest=ScaleBenchmarkTest -Dcheckstyle.skip=true
 * </pre>
 *
 * <p>只测三件事：索引构建耗时（含 Embedding API 往返）、索引占用的堆、全量余弦扫描的延迟分布。
 * 不含 HTTP 层与 LLM，那部分见 loadtest 目录的压测结果。</p>
 */
@EnabledIfSystemProperty(named = "bench", matches = "true")
@DisplayName("规模基准（默认跳过）")
class ScaleBenchmarkTest {

    /** 可用 -Dbench.scales=500,1000 做小规模冒烟，默认是正式三档。 */
    private static final int[] SCALES = System.getProperty("bench.scales") == null
            ? new int[]{5000, 10000, 50000}
            : Arrays.stream(System.getProperty("bench.scales").split(",")).mapToInt(Integer::parseInt).toArray();
    private static final String CORPUS = System.getProperty("bench.corpus", "results/dataset/books-50000.json");
    /** 合成向量模式：不调 Embedding API，只测堆占用与扫描延迟（构建耗时不含网络往返）。 */
    private static final boolean SYNTHETIC = "true".equals(System.getProperty("bench.synthetic"));
    private static final String API_OUT = "results/scale-benchmark.json";
    private static final String SYNTHETIC_OUT = "results/scale-benchmark-synthetic.json";
    private static final int SEARCH_SAMPLES = 300;
    private static final int SEARCH_WARMUP = 30;
    private static final int TOP_K = 3;

    /** 用真实查询做搜索向量的来源，避免随机向量掩盖分词/维度上的真实代价。 */
    private static final List<String> QUERIES = List.of(
            "想学并发编程，有什么书推荐",
            "讲算法的书",
            "数据库性能优化",
            "想了解心理学",
            "机器学习入门",
            "怎么做红烧肉",
            "中国古代历史",
            "经济学入门读物",
            "孩子看的科普书",
            "操作系统原理");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runScaleBenchmark() throws Exception {
        EmbeddingService embedding = SYNTHETIC
                ? null
                : new EmbeddingService("", requireApiKey(), "", new RestTemplate());
        List<Map<String, Object>> corpus = loadCorpus();
        assertTrue(corpus.size() >= SCALES[SCALES.length - 1],
                "语料条数必须覆盖最大档 " + SCALES[SCALES.length - 1] + "，实际 " + corpus.size());

        List<double[]> queryVectors = queryVectors(embedding);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", SYNTHETIC ? "synthetic" : "api");
        result.put("dimensions", queryVectors.get(0).length);
        if (embedding != null) {
            result.put("single_call_avg_ms", round(singleCallMs(embedding, corpus)));
        }
        result.put("scales", new ArrayList<Map<String, Object>>());
        result.put("batch_samples", new ArrayList<Map<String, Object>>());

        buildIndex(corpus, embedding, queryVectors, result);

        Path out = Path.of(SYNTHETIC ? SYNTHETIC_OUT : API_OUT);
        Files.createDirectories(Path.of("results"));
        Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result),
                StandardCharsets.UTF_8);
        System.out.println("[bench] 结果写入 " + out);
    }

    /** 逐条嵌入取 20 条样本，用于和批量接口对比。 */
    private double singleCallMs(EmbeddingService embedding, List<Map<String, Object>> corpus) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            embedding.embed((String) corpus.get(i).get("description"));
        }
        return (System.currentTimeMillis() - start) / 20.0;
    }

    private List<double[]> queryVectors(EmbeddingService embedding) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < QUERIES.size(); i++) {
            out.add(embedding != null ? embedding.embed(QUERIES.get(i)) : randomVector(new Random(i)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void buildIndex(List<Map<String, Object>> corpus, EmbeddingService embedding,
                            List<double[]> queryVectors, Map<String, Object> result) {
        List<Map<String, Object>> scales = (List<Map<String, Object>>) result.get("scales");
        List<Map<String, Object>> batches = (List<Map<String, Object>>) result.get("batch_samples");
        VectorStore store = new VectorStore();
        long buildStart = System.currentTimeMillis();
        long lastMilestone = buildStart;
        int indexed = 0;
        int nextScale = 0;

        for (int offset = 0; offset < corpus.size(); offset += EmbeddingService.MAX_BATCH_SIZE) {
            int end = Math.min(offset + EmbeddingService.MAX_BATCH_SIZE, corpus.size());
            List<String> texts = new ArrayList<>(end - offset);
            List<Book> books = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                texts.add((String) corpus.get(i).get("description"));
                books.add(toBook(corpus.get(i), i + 1L));
            }
            long t0 = System.currentTimeMillis();
            List<double[]> vectors = embedding != null
                    ? embedding.embedBatch(texts)
                    : randomVectors(texts.size(), new Random(offset));
            long stepCost = System.currentTimeMillis() - t0;
            for (int i = 0; i < books.size(); i++) {
                store.put(books.get(i), vectors.get(i));
            }
            indexed = end;

            while (nextScale < SCALES.length && indexed >= SCALES[nextScale]) {
                scales.add(sample(store, queryVectors, buildStart, lastMilestone, SCALES[nextScale]));
                lastMilestone = System.currentTimeMillis();
                nextScale++;
            }
            if (batches.size() < 200) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("batch_index", batches.size());
                b.put("batch_size", texts.size());
                b.put(SYNTHETIC ? "local_generate_ms" : "api_ms", stepCost);
                batches.add(b);
            }
        }
        result.put("indexed", indexed);
        result.put("vector_store_size", store.size());
    }

    private Map<String, Object> sample(VectorStore store, List<double[]> queryVectors,
                                       long buildStart, long lastMilestone, int books) {
        long now = System.currentTimeMillis();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("books", books);
        row.put("index_build_ms", now - buildStart);
        row.put("index_build_since_prev_ms", now - lastMilestone);
        row.put("heap_used_mb", usedHeapMb());
        row.put("scan", measureSearch(store, queryVectors));
        System.out.printf("[bench] %,d 本：建索引 %,d ms，堆 %,.0f MB，扫描 P50 %.2f ms / P95 %.2f ms%n",
                books, now - buildStart, usedHeapMb(),
                ((Map<?, ?>) row.get("scan")).get("p50_ms"),
                ((Map<?, ?>) row.get("scan")).get("p95_ms"));
        return row;
    }

    private String requireApiKey() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("缺少 DASHSCOPE_API_KEY（只测扫描可加 -Dbench.synthetic=true）");
        }
        return key;
    }

    private double[] randomVector(Random random) {
        double[] vector = new double[1024];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = random.nextGaussian();
        }
        return vector;
    }

    private List<double[]> randomVectors(int count, Random random) {
        List<double[]> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(randomVector(random));
        }
        return out;
    }

    private Map<String, Object> measureSearch(VectorStore store, List<double[]> queryVectors) {
        for (int i = 0; i < SEARCH_WARMUP; i++) {
            store.search(queryVectors.get(i % queryVectors.size()), TOP_K);
        }
        long[] samples = new long[SEARCH_SAMPLES];
        for (int i = 0; i < SEARCH_SAMPLES; i++) {
            long t0 = System.nanoTime();
            store.search(queryVectors.get(i % queryVectors.size()), TOP_K);
            samples[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("samples", SEARCH_SAMPLES);
        out.put("p50_ms", round(samples[SEARCH_SAMPLES / 2] / 1e6));
        out.put("p95_ms", round(samples[(int) (SEARCH_SAMPLES * 0.95)] / 1e6));
        out.put("p99_ms", round(samples[(int) (SEARCH_SAMPLES * 0.99)] / 1e6));
        out.put("max_ms", round(samples[SEARCH_SAMPLES - 1] / 1e6));
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadCorpus() throws Exception {
        return objectMapper.readValue(Path.of(CORPUS).toFile(), List.class);
    }

    private Book toBook(Map<String, Object> row, long id) {
        Book book = new Book((String) row.get("isbn"), (String) row.get("title"),
                (String) row.get("author"), BookStatus.valueOf((String) row.get("status")),
                (String) row.get("location"), (String) row.get("description"));
        book.setId(id);
        return book;
    }

    private double usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024.0 / 1024.0;
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
