package com.library.agent;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

/**
 * 全量上下文下的端到端集成测试：走真实的 Filter / Actuator / springdoc，
 * 只把 ReActAgent 替换成 mock，避免调用真实 LLM。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("API 端到端集成测试")
class ApiIntegrationTest {

    private static final String CHAT_BODY = """
            {"model":"qwen-plus","stream":false,"messages":[{"role":"user","content":"你好"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReActAgent agent;

    @Test
    @DisplayName("健康检查暴露数据库与向量索引组件")
    void health_shouldExposeDbAndVectorStoreComponents() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                // 测试 profile 关闭了向量初始化，索引为空应报 UNKNOWN 而非 DOWN，
                // 否则 /actuator/health 会返回 503 使容器健康检查失败
                .andExpect(jsonPath("$.components.vectorStore.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.vectorStore.details.indexedBooks").value(0));
    }

    @Test
    @DisplayName("OpenAPI 文档包含对话与健康检查两个接口")
    void apiDocs_shouldContainEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v1/chat/completions")))
                .andExpect(content().string(containsString("/api/health")))
                .andExpect(content().string(containsString("ErrorResponse")));
    }

    @Test
    @DisplayName("Swagger UI 可访问")
    void swaggerUi_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("对话接口返回 OpenAI 兼容结构")
    void chat_shouldReturnOpenAiCompatibleResponse() throws Exception {
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(reply("您好，有什么可以帮您的？")));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].finish_reason").value("stop"));
    }

    @Test
    @DisplayName("参数校验失败返回统一错误体，含错误码与 traceId")
    void chat_shouldReturnUnifiedErrorBody_onValidationFailure() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"model":"qwen-plus","messages":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.path").value("/v1/chat/completions"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("响应回显调用方传入的 X-Request-Id")
    void shouldEchoIncomingRequestId() throws Exception {
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(reply("ok")));

        mockMvc.perform(post("/v1/chat/completions")
                        .header("X-Request-Id", "trace-e2e-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-e2e-1"));
    }

    @Test
    @DisplayName("旧版 /api/health 接口保持可用，未被 Actuator 取代")
    void legacyHealthEndpoint_shouldStillWork() throws Exception {
        when(agent.getName()).thenReturn("图书馆助手");

        mockMvc.perform(post("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.agent").value("图书馆助手"));
    }

    /**
     * 以下三条守卫一组真实存在过的缺陷：{@code @ExceptionHandler(Exception.class)}
     * 会把 Spring MVC 本应直接返回的 404/405/400 全部吞成 500，
     * 导致客户端无法区分"自己请求写错了"和"服务端故障"。
     */
    @Test
    @DisplayName("不存在的路径返回 404 与错误码 40401，而不是 500")
    void unknownPath_shouldReturn404() throws Exception {
        mockMvc.perform(get("/no/such/path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.path").value("/no/such/path"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("用 GET 调用只接受 POST 的接口返回 405 与错误码 40501")
    void wrongHttpMethod_shouldReturn405() throws Exception {
        mockMvc.perform(get("/v1/chat/completions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40501));
    }

    @Test
    @DisplayName("请求体不是合法 JSON 时返回 400 与错误码 40001")
    void malformedJsonBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not a json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体不是合法的 JSON"));
    }

    private Msg reply(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }
}
