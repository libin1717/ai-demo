package com.libin.springai.aideepseek.controller;

import com.libin.springai.aideepseek.common.PromptConstants;
import com.libin.springai.aideepseek.dto.MsgInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@code @User} libin
 * {@code @Data} 2025/10/21
 * {@code @Version} 1.0
 * {@code @Description}
 */
@Slf4j
@RestController
@RequestMapping("/sse/chat/completions")
@CrossOrigin(origins = "*") //
public class StreamChatCompletionsController {

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    // 存储所有连接的客户端
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 为不同的会话存储聊天记忆
    private static Map<String, List<Message>> chatMemoryStore = new ConcurrentHashMap();

    /**
     * 注册 SSE 连接
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam String clientId) {
        System.out.println("客户端连接: " + clientId);

        SseEmitter emitter = new SseEmitter(3600000L); // 1小时超时

        // 存储 emitter
        emitters.put(clientId, emitter);

        // 发送连接成功消息
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(createMessage("连接成功", "connected"))
                    .name("connected")
                    .id("1")
                    .reconnectTime(5000L);
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        // 设置回调
        emitter.onCompletion(() -> {
            System.out.println("客户端断开连接: " + clientId);
            emitters.remove(clientId);
        });

        emitter.onTimeout(() -> {
            System.out.println("客户端连接超时: " + clientId);
            emitters.remove(clientId);
        });

        emitter.onError((ex) -> {
            System.out.println("客户端连接错误: " + clientId + ", 错误: " + ex.getMessage());
            emitters.remove(clientId);
        });

        return emitter;
    }

    /**
     * 向特定客户端发送消息
     */
    @PostMapping("/send-to-client")
    public String sendToClient(@RequestBody MsgInfoDTO msgInfoDTO) {

        String clientId = msgInfoDTO.getClientId();
        SseEmitter emitter = emitters.get(clientId);
        String message = msgInfoDTO.getMessage();

        // 1.获取或创建指定sessionId的记忆体
        List<Message> messageList = chatMemoryStore.getOrDefault(clientId, new ArrayList<>());

        /*简化问题*/
        message = simplifyQ(message);
//        System.out.printf("简化后的问答：" + message);
//        message += "，用100字以内回答";

        if (PromptConstants.WRITER_TOOL.equals(msgInfoDTO.getPromptType()) && messageList.isEmpty()) {
            messageList.add(new UserMessage(PromptConstants.WRITER_TOOL_PROMPT));
        }

        // 2.将用户的新消息添加到记忆体中
        messageList.add(new UserMessage(message));

        if (emitter != null) {
            try {
                DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                        /*模型类型*/
                        .model("deepseek-reasoner")
                        .build();

                Prompt prompt = new Prompt(messageList, options);

                // 2. 调用模型，传入包含完整历史的Prompt
                Flux<ChatResponse> flux = deepSeekChatModel.stream(prompt);

                sendMessage(emitter, "\\r\\n【思考内容】 \\r\\n");
                flux.toIterable().forEach(
                        res -> {
                            if (0 != res.getMetadata().getUsage().getTotalTokens()) {
                                System.out.println("totalToken: " + res.getMetadata().getUsage().getTotalTokens());
                            }
                            DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                            //长连接未提供响应内容时，会返回 null
                            if (Objects.nonNull(output.getReasoningContent())) {
                                sendMessage(emitter, output.getReasoningContent());
                            }
                        }
                );

                sendMessage(emitter, " \\r\\n【正文】 \\r\\n");

                StringBuilder aiResponse = new StringBuilder();
                flux.toIterable().forEach(
                        res -> {
                            if (0 != res.getMetadata().getUsage().getTotalTokens()) {
                                System.out.println("totalToken: " + res.getMetadata().getUsage().getTotalTokens());
                            }
                            DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                            if (Objects.nonNull(output.getText())) {
                                sendMessage(emitter, output.getText());
                                aiResponse.append(output.getText());
                            }
                        }
                );

                // 3. 将模型的回复也添加到记忆体中
                /*简化回答*/
                String simplifyA = simplifyA(aiResponse.toString());
                System.out.println("简化后的答案为：" + simplifyA);

                messageList.add(new AssistantMessage(aiResponse.toString()));

                // 4. 重新保存记忆体
                chatMemoryStore.put(clientId, messageList);

            } catch (Exception e) {
                log.error("", e);
                emitters.remove(clientId);
                return "消息发送失败: " + e.getMessage();
            }

        } else {
            return "客户端未连接";
        }
        sendMessage(emitter, "chat done");
        return "done";

    }

    /**
     * 简化问答
     *
     * @param message 问答
     */
    private String simplifyQ(String message) {
        /*直接替换*/
//        message = message.replace("你好", "").replace("请问", "");

        /*使用其它能力优化问答*/
       /* String format = String.format("请帮我简化下列问答语句,并直接回复我答案: %s", message);
        // 根据关键字提取问答中的回答 答案替换原有语句
        message = deepSeekChatModel.call(format);*/

        return message;

    }

    /**
     * 简化答案
     *
     * @param message 问答
     */
    private String simplifyA(String message) {

        /*使用其它能力优化问答*/
       /* String format = String.format("请帮我简化下列问答语句,并直接回复我答案: %s", message);
        // 根据关键字提取问答中的回答 答案替换原有语句
        message = deepSeekChatModel.call(format);*/

        return message;

    }

    /**
     * 发送消息给客户端
     *
     * @param emitter sse
     * @param message 消息
     */
    private void sendMessage(SseEmitter emitter, String message) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(createMessage(message, "notification"))
                    .name("notification")
                    .id(String.valueOf(System.currentTimeMillis()));
            emitter.send(event);
        } catch (IOException e) {
            log.error("发送消息失败:", e);
        }
    }

    /**
     * 广播消息给所有客户端
     */
    @PostMapping("/broadcast")
    public String broadcast(@RequestParam String message) {
        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event()
                        .data(createMessage(message, "broadcast"))
                        .name("broadcast")
                        .id(String.valueOf(System.currentTimeMillis()));
                entry.getValue().send(event);
                successCount++;
            } catch (IOException e) {
                emitters.remove(entry.getKey());
                failCount++;
            }
        }

        return String.format("广播完成: 成功 %d, 失败 %d", successCount, failCount);
    }

    /**
     * 启动定时消息推送
     */
    @PostMapping("/start-timer")
    public String startTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            String timeMessage = "定时消息: " + LocalDateTime.now();
            broadcast(timeMessage);
        }, 0, 5, TimeUnit.SECONDS);

        return "定时推送已启动";
    }

    /**
     * 停止定时消息推送
     */
    @PostMapping("/stop-timer")
    public String stopTimer() {
        scheduler.shutdown();
        return "定时推送已停止";
    }

    /**
     * 获取连接客户端数量
     */
    @GetMapping("/client-count")
    public Map<String, Object> getClientCount() {
        return Map.of(
                "clientCount", emitters.size(),
                "clients", emitters.keySet()
        );
    }

    private String createMessage(String content, String type) {
        return String.format("{\"type\": \"%s\", \"content\": \"%s\", \"timestamp\": \"%s\"}",
                type, content, LocalDateTime.now());
    }

}
