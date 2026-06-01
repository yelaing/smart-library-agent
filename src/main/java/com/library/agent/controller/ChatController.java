package com.library.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.agent.dto.ChatRequest;
import com.library.agent.dto.ChatResponse;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.chat.completions.streaming.ChatCompletionsStreamingAdapter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ReActAgent agent;
    private final ChatCompletionsStreamingAdapter streamingAdapter;
    private final ObjectMapper objectMapper;

    public ChatController(ReActAgent agent) {
        this.agent = agent;
        this.streamingAdapter = new ChatCompletionsStreamingAdapter();
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
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

        String model = request.getModel() != null ? request.getModel() : "qwen-plus";

        if (request.isStream()) {
            String requestId = java.util.UUID.randomUUID().toString();
            Flux<String> sseFlux = streamingAdapter
                    .stream(agent, List.of(userMsg), requestId, model)
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
                    .concatWith(Flux.just("[DONE]\n\n"));

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseFlux);
        }

        Msg response = agent.call(userMsg).block();

        String replyText = "";
        if (response != null) {
            replyText = response.getContent().stream()
                    .filter(block -> block instanceof TextBlock)
                    .map(block -> ((TextBlock) block).getText())
                    .reduce("", (a, b) -> a + b);
        }

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
        resp.setId(java.util.UUID.randomUUID().toString());
        resp.setCreated(System.currentTimeMillis() / 1000);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "agent", agent.getName());
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
