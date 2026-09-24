package com.library.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "对话请求，兼容 OpenAI Chat Completions 协议")
public class ChatRequest {

    @Schema(description = "模型名，仅回显用；实际使用的模型由服务端 agentscope.dashscope.model-name 配置决定",
            example = "qwen-plus")
    private String model;

    @Schema(description = "是否以 SSE 流式返回，流以 [DONE] 结束", example = "false")
    private boolean stream;

    @Schema(description = "消息列表，取最后一条 role=user 的消息作为本次输入")
    private List<Message> messages;

    public String getModel() { return model; }
    public boolean isStream() { return stream; }
    public List<Message> getMessages() { return messages; }

    public void setModel(String model) { this.model = model; }
    public void setStream(boolean stream) { this.stream = stream; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    @Schema(description = "单条消息")
    public static class Message {

        @Schema(description = "角色，取值 user / assistant / system", example = "user")
        private String role;

        @Schema(description = "消息内容，不能为空或纯空白", example = "搜索 Spring 相关的书")
        private String content;

        public String getRole() { return role; }
        public String getContent() { return content; }
        public void setRole(String role) { this.role = role; }
        public void setContent(String content) { this.content = content; }
    }
}
