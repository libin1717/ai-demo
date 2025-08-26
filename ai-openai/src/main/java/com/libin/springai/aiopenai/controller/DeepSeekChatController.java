package com.libin.springai.aiopenai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author libin
 * @Data 2025/8/24 20:35
 * @Version 1.0
 * @Description
 */

@RestController
@RequestMapping("/deepseek/chat")
public class DeepSeekChatController {

    private static final String DEFAULT_PROMPT = "你是一个聊天助手，请根据用户问题，进行简短的回答！";

    private ChatClient chatClient;


    public DeepSeekChatController(ChatClient.Builder builder) {
        this.chatClient = builder
//                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }


    @GetMapping("/modelChatForOpenAi")
    public String simpleChat(@RequestParam String message) {
        String content = chatClient.prompt(message).call().content();
        System.out.println(content);
        return content;
    }


}
