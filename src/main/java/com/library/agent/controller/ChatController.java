package com.library.agent.controller;

import com.library.agent.dto.ChatRequest;
import com.library.agent.dto.ChatResponse;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.scheduler.Schedulers;

/**
 * OpenAI 兼容的 Chat Completions 接口。
 * POST /v1/chat/completions
 */
@RestController
public class ChatController {

    private final ReActAgent agent;

    public ChatController(ReActAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/v1/chat/completions")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String userContent = extractLastUserMessage(request.messages());
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(userContent).build())
                .build();

        Msg response = agent.call(userMsg)
                .subscribeOn(Schedulers.boundedElastic())
                .block();

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
        resp.setModel(request.model() != null ? request.model() : "qwen-plus");
        resp.setObject("chat.completion");
        resp.setId(java.util.UUID.randomUUID().toString());
        resp.setCreated(System.currentTimeMillis() / 1000);

        return resp;
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
            if ("user".equals(messages.get(i).role())) {
                return messages.get(i).content();
            }
        }
        return messages.get(messages.size() - 1).content();
    }
}
