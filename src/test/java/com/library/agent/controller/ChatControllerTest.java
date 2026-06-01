package com.library.agent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

@WebMvcTest(ChatController.class)
@DisplayName("聊天控制器测试")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReActAgent agent;

    @Test
    @DisplayName("健康检查返回 ok 状态")
    void health_shouldReturnOk() throws Exception {
        when(agent.getName()).thenReturn("图书馆助手");

        mockMvc.perform(post("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.agent").value("图书馆助手"));
    }

    @Test
    @DisplayName("聊天接口返回 OpenAI 兼容格式")
    void chat_shouldReturnOpenAICompatibleResponse() throws Exception {
        Msg mockResponse = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("您好，有什么可以帮您的？").build())
                .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(mockResponse));

        String requestBody = """
                {
                  "model": "qwen-plus",
                  "stream": false,
                  "messages": [
                    {"role": "user", "content": "你好"}
                  ]
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content").value("您好，有什么可以帮您的？"))
                .andExpect(jsonPath("$.choices[0].finish_reason").value("stop"));
    }

    @Test
    @DisplayName("未指定 model 时使用默认 qwen-plus")
    void chat_shouldUseDefaultModel_whenModelNotProvided() throws Exception {
        Msg mockResponse = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("ok").build())
                .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(mockResponse));

        String requestBody = """
                {
                  "stream": false,
                  "messages": [
                    {"role": "user", "content": "你好"}
                  ]
                }
                """;

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("qwen-plus"));
    }

    @Test
    @DisplayName("空消息数组返回 400 错误")
    void chat_shouldReturn400_whenMessagesEmpty() throws Exception {
        String requestBody = """
                {"model":"qwen-plus","messages":[]}""";

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("messages 不能为空"));
    }

    @Test
    @DisplayName("空白消息内容返回 400 错误")
    void chat_shouldReturn400_whenContentBlank() throws Exception {
        String requestBody = """
                {"model":"qwen-plus","messages":[{"role":"user","content":"   "}]}""";

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("消息内容不能为空"));
    }
}
