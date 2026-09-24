package com.library.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "对话响应，兼容 OpenAI Chat Completions 协议")
public class ChatResponse {

    @Schema(description = "本次响应的唯一标识")
    private String id;

    @Schema(description = "对象类型，固定为 chat.completion", example = "chat.completion")
    private String object;

    @Schema(description = "Unix 时间戳（秒）")
    private long created;

    @Schema(description = "模型名")
    private String model;

    @Schema(description = "候选回复列表，固定返回 1 条")
    private List<Choice> choices;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    @Schema(description = "单条候选回复")
    public static class Choice {

        @Schema(description = "候选序号", example = "0")
        private int index;

        @JsonProperty("finish_reason")
        @Schema(description = "结束原因，正常返回为 stop", example = "stop")
        private String finishReason;

        @Schema(description = "回复消息")
        private Message message;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }
    }

    @Schema(description = "回复消息")
    public static class Message {

        @Schema(description = "角色，固定为 assistant", example = "assistant")
        private String role;

        @Schema(description = "回复正文")
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
