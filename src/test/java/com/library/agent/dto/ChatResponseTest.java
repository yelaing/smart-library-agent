package com.library.agent.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatResponse 序列化测试")
class ChatResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("finish_reason 字段使用 snake_case 序列化")
    void shouldSerializeFinishReasonAsSnakeCase() throws Exception {
        ChatResponse.Message msg = new ChatResponse.Message();
        msg.setRole("assistant");
        msg.setContent("hello");

        ChatResponse.Choice choice = new ChatResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason("stop");
        choice.setMessage(msg);

        ChatResponse resp = new ChatResponse();
        resp.setId("test-123");
        resp.setObject("chat.completion");
        resp.setCreated(1700000000L);
        resp.setModel("qwen-plus");
        resp.setChoices(List.of(choice));

        String json = mapper.writeValueAsString(resp);

        assertTrue(json.contains("\"finish_reason\""));
        assertFalse(json.contains("finishReason"));
        assertTrue(json.contains("\"object\":\"chat.completion\""));
        assertTrue(json.contains("\"id\":\"test-123\""));
        assertTrue(json.contains("\"choices\""));
    }

    @Test
    @DisplayName("空 choices 数组正常序列化")
    void shouldHandleEmptyChoices() throws Exception {
        ChatResponse resp = new ChatResponse();
        resp.setId("empty");
        resp.setObject("chat.completion");
        resp.setCreated(1700000000L);
        resp.setModel("qwen-plus");
        resp.setChoices(List.of());

        String json = mapper.writeValueAsString(resp);

        assertTrue(json.contains("\"choices\":[]"));
    }
}
