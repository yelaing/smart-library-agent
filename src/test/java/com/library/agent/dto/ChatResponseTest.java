package com.library.agent.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
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
