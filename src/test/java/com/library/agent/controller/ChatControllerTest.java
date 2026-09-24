package com.library.agent.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebMvcTest(ChatController.class)
@TestPropertySource(properties = "library.chat.timeout=150ms")
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
                .andExpect(jsonPath("$.message").value("messages 不能为空"))
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
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

    @Test
    @DisplayName("Agent 超过配置时长后返回 504 与错误码 50401")
    void chat_shouldReturn504_whenAgentExceedsTimeout() throws Exception {
        // Mono.never() 永不完成，由 library.chat.timeout=150ms 触发真实超时链路
        when(agent.call(any(Msg.class))).thenReturn(Mono.never());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"qwen-plus","messages":[{"role":"user","content":"你好"}]}"""))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(50401))
                .andExpect(jsonPath("$.message").value(containsString("Agent 调用超时")));
    }

    @Test
    @DisplayName("Agent 下游超时异常同样映射为 504")
    void chat_shouldReturn504_whenAgentEmitsTimeoutException() throws Exception {
        when(agent.call(any(Msg.class))).thenReturn(Mono.error(new TimeoutException("upstream timed out")));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"qwen-plus","messages":[{"role":"user","content":"你好"}]}"""))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(50401));
    }

    @Test
    @DisplayName("Agent 抛出其他异常时返回 500 与错误码 50000，且不泄漏内部信息")
    void chat_shouldReturn500_whenAgentFails() throws Exception {
        when(agent.call(any(Msg.class))).thenReturn(Mono.error(new IllegalStateException("api-key 无效")));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"qwen-plus","messages":[{"role":"user","content":"你好"}]}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误，请稍后重试"));
    }

    @Test
    @DisplayName("响应头回显 X-Request-Id")
    void chat_shouldEchoRequestIdHeader() throws Exception {
        Msg mockResponse = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text("ok").build())
                .build();
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(mockResponse));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("X-Request-Id", "trace-from-client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"qwen-plus","messages":[{"role":"user","content":"你好"}]}"""))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-from-client"));
    }

    @Test
    @DisplayName("stream=true 时返回 SSE 流并以 [DONE] 结束")
    void chat_shouldReturnSseStreamEndingWithDone() throws Exception {
        when(agent.stream(anyList(), any())).thenReturn(Flux.empty());

        MvcResult mvcResult = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"model":"qwen-plus","stream":true,"messages":[{"role":"user","content":"你好"}]}"""))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("[DONE]")));
    }
}
