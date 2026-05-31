package com.library.agent.dto;

import java.util.List;

public class ChatRequest {
    private String model;
    private boolean stream;
    private List<Message> messages;

    public String model() { return model; }
    public boolean stream() { return stream; }
    public List<Message> messages() { return messages; }

    public void setModel(String model) { this.model = model; }
    public void setStream(boolean stream) { this.stream = stream; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public static class Message {
        private String role;
        private String content;

        public String role() { return role; }
        public String content() { return content; }
        public void setRole(String role) { this.role = role; }
        public void setContent(String content) { this.content = content; }
    }
}
