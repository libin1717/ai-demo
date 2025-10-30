package com.libin.springai.aideepseek.dto;

import lombok.Data;

import java.util.List;

/**
 * {@code @User} libin
 * {@code @Data} 2025/10/21
 * {@code @Version} 1.0
 * {@code @Description}
 */
@Data
public class ChatRequest {
    // 指定模型
    private String model = "deepseek-chat";
    private List<Message> messages;
    // 明确开启流式传输
    private boolean stream = true;

    @Data
    public static class Message {
        private String role;
        private String content;
    }
}
