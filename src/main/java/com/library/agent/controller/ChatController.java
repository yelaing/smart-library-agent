package com.library.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.agent.dto.ChatRequest;
import com.library.agent.dto.ChatResponse;
import com.library.agent.dto.ErrorResponse;
import com.library.agent.exception.AgentTimeoutException;
import com.library.agent.service.AgentSessionManager;
import io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Tag(name = "对话", description = "OpenAI 兼容的对话接口")
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** 调用方标识会话的请求头。同一会话复用同一份记忆，缺省时按无状态单轮处理。 */
    static final String SESSION_HEADER = "X-Session-Id";

    private final AgentSessionManager sessions;
    private final ChatCompletionsStreamingAdapter streamingAdapter;
    private final ObjectMapper objectMapper;
    private final Duration agentTimeout;

    public ChatController(AgentSessionManager sessions, @Value("${library.chat.timeout:60s}") Duration agentTimeout) {
        this.sessions = sessions;
        this.streamingAdapter = new ChatCompletionsStreamingAdapter();
        this.objectMapper = new ObjectMapper();
        this.agentTimeout = agentTimeout;
    }

    @Operation(summary = "对话补全",
            description = """
                    接收 OpenAI 兼容的 messages，取最后一条 role=user 的消息交给 ReAct Agent 推理，
                    Agent 按需调用图书工具后返回回复。stream=true 时以 text/event-stream 分片返回，
                    并以 [DONE] 结束；stream=false 时一次性返回 OpenAI 兼容结构。""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功，返回 OpenAI 兼容结构或 SSE 流"),
            @ApiResponse(responseCode = "400", description = "messages 为空，或最后一条用户消息内容为空白",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Agent 调用超过 library.chat.timeout 上限",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "服务端内部错误",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request,
                                  @RequestHeader(value = SESSION_HEADER, required = false) String sessionId) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }
        String userContent = extractLastUserMessage(request.getMessages());
        if (userContent.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(userContent).build())
                .build();
        AgentSessionManager.SessionContext session = sessions.forSession(sessionId);

        String model = request.getModel() != null ? request.getModel() : "qwen-plus";
        // 只记录长度，不落内容，避免把读者输入写进日志
        log.info("收到对话请求: model={}, stream={}, 输入字符数={}", model, request.isStream(), userContent.length());

        if (request.isStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(streamReply(session, userMsg, model));
        }

        long start = System.currentTimeMillis();
        Msg response = callAgent(session, userMsg);
        String replyText = extractText(response);
        log.info("Agent 调用完成: model={}, 回复字符数={}, cost={}ms", model, replyText.length(),
                System.currentTimeMillis() - start);

        return ResponseEntity.ok(toChatResponse(replyText, model));
    }

    /**
     * 订阅时获取会话闸门，流结束/取消/出错时释放，保证同一会话的多个流不交错写记忆。
     */
    private Flux<String> streamReply(AgentSessionManager.SessionContext session, Msg userMsg, String model) {
        String requestId = UUID.randomUUID().toString();
        return Flux.using(
                () -> {
                    session.acquire();
                    return session;
                },
                held -> streamingAdapter
                        .stream(held.agent(), List.of(userMsg), requestId, model)
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(chunk -> {
                            try {
                                return objectMapper.writeValueAsString(chunk) + "\n\n";
                            } catch (Exception e) {
                                log.warn("SSE 序列化失败: {}", e.getMessage());
                                return "{\"error\":\"serialization failed\"}\n\n";
                            }
                        })
                        .onErrorResume(e -> {
                            log.error("Agent 流式调用失败", e);
                            return Flux.just("{\"error\":\"" + e.getMessage() + "\"}\n\n", "[DONE]\n\n");
                        })
                        .concatWith(Flux.just("[DONE]\n\n")),
                AgentSessionManager.SessionContext::release);
    }

    /**
     * 非流式调用，持有会话闸门直到模型返回。
     *
     * <p>timeout 抛的是受检 TimeoutException，block() 会把它包成 ReactiveException，
     * 因此在 Reactor 链路上先转成自定义异常，交给 GlobalExceptionHandler 映射为 504。</p>
     */
    private Msg callAgent(AgentSessionManager.SessionContext session, Msg userMsg) {
        session.acquire();
        try {
            return session.agent().call(userMsg)
                    .timeout(agentTimeout)
                    .onErrorMap(TimeoutException.class,
                            e -> new AgentTimeoutException(
                                    "Agent 调用超时（" + agentTimeout.toSeconds() + " 秒），请稍后重试", e))
                    .block();
        } finally {
            session.release();
        }
    }

    private String extractText(Msg response) {
        if (response == null) {
            return "";
        }
        return response.getContent().stream()
                .filter(block -> block instanceof TextBlock)
                .map(block -> ((TextBlock) block).getText())
                .reduce("", (a, b) -> a + b);
    }

    private ChatResponse toChatResponse(String replyText, String model) {
        ChatResponse.Message msg = new ChatResponse.Message();
        msg.setRole("assistant");
        msg.setContent(replyText);

        ChatResponse.Choice choice = new ChatResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason("stop");
        choice.setMessage(msg);

        ChatResponse resp = new ChatResponse();
        resp.setChoices(List.of(choice));
        resp.setModel(model);
        resp.setObject("chat.completion");
        resp.setId(UUID.randomUUID().toString());
        resp.setCreated(System.currentTimeMillis() / 1000);
        return resp;
    }

    @Operation(summary = "轻量健康检查",
            description = "返回服务名与 agent 名称。含数据库、向量索引等依赖状态的完整健康信息见 GET /actuator/health")
    @PostMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "agent", sessions.agentName());
    }

    private String extractLastUserMessage(List<ChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                return messages.get(i).getContent();
            }
        }
        return messages.get(messages.size() - 1).getContent();
    }
}
