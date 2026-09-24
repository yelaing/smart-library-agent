package com.library.agent.health;

import com.library.agent.service.VectorStore;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 向量索引健康指示，在 /actuator/health 中暴露为 {@code vectorStore} 组件。
 *
 * <p>索引为空时上报 UNKNOWN 而非 DOWN：索引未构建属于"降级"而不是"故障"
 * —— 缺少 API Key、Embedding 接口暂时不可用都会导致索引为空，
 * 而这并不影响查书/借书/还书等主要功能。若上报 DOWN，
 * /actuator/health 会返回 503，容器健康检查会判定失败并反复重启。</p>
 */
@Component
public class VectorStoreHealthIndicator implements HealthIndicator {

    private final VectorStore vectorStore;

    public VectorStoreHealthIndicator(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Health health() {
        int indexedBooks = vectorStore.size();
        if (indexedBooks == 0) {
            return Health.unknown()
                    .withDetail("indexedBooks", 0)
                    .withDetail("reason", "向量索引为空，语义推荐不可用")
                    .build();
        }
        return Health.up().withDetail("indexedBooks", indexedBooks).build();
    }
}
