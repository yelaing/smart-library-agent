package com.library.agent.config;

import com.library.agent.repository.BookRepository;
import com.library.agent.repository.BorrowRecordRepository;
import com.library.agent.service.RecommendationService;
import com.library.agent.tools.LibraryTool;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Agent 核心配置 - 手动组装 ReActAgent 的各个组件。
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${agentscope.dashscope.api-key}")
    private String apiKey;

    @Value("${agentscope.dashscope.model-name:qwen-plus}")
    private String modelName;

    /** 留空走官方地址；压测时指向本地 mock，避免消耗真实额度。 */
    @Value("${agentscope.dashscope.base-url:}")
    private String baseUrl;

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        if (apiKey == null || apiKey.isBlank()) {
            // 显式失败优于静默失败：五个图书工具只通过 LLM tool calling 暴露，
            // 没有其它 HTTP 入口，缺 key 时服务实际不提供任何功能。
            // 若不在这里拦住，AgentScope 只会抛出难懂的 "API key is required"。
            // 单独打一条 ERROR，避免可操作的提示被埋在 Bean 创建的异常链里。
            log.error("DASHSCOPE_API_KEY 未配置，服务无法启动。请复制 .env.example 为 .env 并填入百炼 API Key，"
                    + "或设置环境变量 DASHSCOPE_API_KEY=sk-xxx（获取地址 https://bailian.console.aliyun.com/）");
            throw new IllegalStateException("DASHSCOPE_API_KEY 未配置，服务无法启动");
        }
        var builder = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(GenerateOptions.builder().thinkingBudget(1024).build());
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
            log.warn("Chat 模型指向自定义地址: {}（仅用于本地压测，生产环境不应设置）", baseUrl);
        }
        return builder.build();
    }

    @Bean
    public Toolkit toolkit(BookRepository bookRepository,
                           BorrowRecordRepository borrowRecordRepository,
                           TransactionTemplate transactionTemplate,
                           RecommendationService recommendationService) {
        LibraryTool libraryTool = new LibraryTool(bookRepository, borrowRecordRepository, transactionTemplate,
                recommendationService);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(libraryTool);
        return toolkit;
    }
}
