package com.library.agent.config;

import com.library.agent.repository.BookRepository;
import com.library.agent.repository.BorrowRecordRepository;
import com.library.agent.service.EmbeddingService;
import com.library.agent.service.VectorStore;
import com.library.agent.tools.LibraryTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Agent 核心配置 - 手动组装 ReActAgent 的各个组件。
 */
@Configuration
public class AgentConfig {

    @Value("${agentscope.dashscope.api-key}")
    private String apiKey;

    @Value("${agentscope.dashscope.model-name:qwen-plus}")
    private String modelName;

    @Value("${agentscope.agent.name:图书馆助手}")
    private String agentName;

    @Value("${agentscope.agent.sys-prompt:你是学校图书馆的智能助手，帮助读者查书、借书、还书。}")
    private String sysPrompt;

    @Bean
    public DashScopeChatModel dashScopeChatModel() {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(GenerateOptions.builder().thinkingBudget(1024).build())
                .build();
    }

    @Bean
    public Toolkit toolkit(BookRepository bookRepository,
                           BorrowRecordRepository borrowRecordRepository,
                           TransactionTemplate transactionTemplate,
                           EmbeddingService embeddingService,
                           VectorStore vectorStore) {
        LibraryTool libraryTool = new LibraryTool(bookRepository, borrowRecordRepository, transactionTemplate,
                embeddingService, vectorStore);
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(libraryTool);
        return toolkit;
    }

    @Bean
    public ReActAgent reActAgent(DashScopeChatModel model, Toolkit toolkit) {
        return ReActAgent.builder()
                .name(agentName)
                .sysPrompt(sysPrompt)
                .model(model)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .build();
    }
}
