package com.libin.springai.aideepseek.controller;

import com.libin.springai.aideepseek.service.ChatService;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/sse/chat")
@CrossOrigin(origins = "*") //
public class StreamChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    // 存储所有连接的客户端
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

/*    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamCompletions(@RequestBody ChatRequest request) {
        // 通过Service层处理，将DeepSeek的流包装成SSE事件流
        return chatService.streamWithDeepSeek(request);
    }*/

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
    public String sendToClient(@RequestParam String clientId,
                               @RequestParam String message) {
        SseEmitter emitter = emitters.get(clientId);

        if (emitter != null) {
            try {
                DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                        /*模型类型*/
                        .model("deepseek-reasoner")
                        .build();

                Prompt prompt = new Prompt(message, options);
                Flux<ChatResponse> flux = deepSeekChatModel.stream(prompt);

                sendMessage(emitter, "\\r\\n【思考内容】 \\r\\n");
                flux.toIterable().forEach(
                        res -> {
                            DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                            //长连接未提供响应内容时，会返回 null
                            if (Objects.nonNull(output.getReasoningContent())) {
                                sendMessage(emitter, output.getReasoningContent());
                            }
                        }
                );

                sendMessage(emitter, " \\r\\n【正文】 \\r\\n");

                flux.toIterable().forEach(
                        res -> {
                            DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                            if (Objects.nonNull(output.getText())) {
                                sendMessage(emitter, output.getText());
                            }
                        }
                );

            } catch (Exception e) {
                log.error("", e);
                emitters.remove(clientId);
                return "消息发送失败: " + e.getMessage();
            }

        } else {
            return "客户端未连接";
        }
        return "done";

    }

    private static void sendMessage(SseEmitter emitter, String message) {
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
            String timeMessage = "定时消息: " + java.time.LocalDateTime.now();
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

    private static String createMessage(String content, String type) {
        return String.format("{\"type\": \"%s\", \"content\": \"%s\", \"timestamp\": \"%s\"}",
                type, content, java.time.LocalDateTime.now());
    }

}
