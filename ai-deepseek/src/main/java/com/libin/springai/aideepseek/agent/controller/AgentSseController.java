package com.libin.springai.aideepseek.agent.controller;

import com.libin.springai.aideepseek.agent.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hermes Agent SSE 流式控制器，提供基于 Server-Sent Events 的实时 Agent 对话能力。
 * <p>
 * 遵循 {@code StreamChatCompletionsController} 的 SSE 模式：
 * <ul>
 *   <li>客户端通过 GET /agent/sse/connect 注册连接</li>
 *   <li>通过 POST /agent/sse/send 发送消息并流式接收 Agent 各阶段进度</li>
 *   <li>支持超时、错误和完成回调自动清理连接</li>
 * </ul>
 * 事件类型包括 thinking、memory、reply_chunk、complete。
 */
@Slf4j
@RestController
@RequestMapping("/agent/sse")
@CrossOrigin(origins = "*")
public class AgentSseController {

    @Autowired
    private AgentService agentService;

    /** 存储所有已连接的客户端，key 为 clientId */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 执行 Agent 循环的线程池，避免无限制创建线程 */
    private final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(4);

    /**
     * 注册 SSE 连接。
     * <p>
     * 创建 1 小时超时的 SseEmitter，注册生命周期回调（完成、超时、错误时自动移除）。
     *
     * @param clientId 客户端唯一标识
     * @return SseEmitter 实例供 Spring MVC 管理
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam String clientId) {
        log.info("Agent SSE client connecting: {}", clientId);

        SseEmitter emitter = new SseEmitter(3600000L); // 1小时超时
        emitters.put(clientId, emitter);

        // 发送连接成功事件
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(buildEventJson("connected", "Agent SSE 连接成功"))
                    .name("connected")
                    .id("1")
                    .reconnectTime(5000L);
            emitter.send(event);
        } catch (IOException e) {
            log.error("Failed to send connected event to {}", clientId, e);
            emitter.completeWithError(e);
            emitters.remove(clientId);
        }

        // 生命周期回调
        emitter.onCompletion(() -> {
            log.info("Agent SSE client disconnected: {}", clientId);
            emitters.remove(clientId);
        });
        emitter.onTimeout(() -> {
            log.info("Agent SSE client timeout: {}", clientId);
            emitters.remove(clientId);
        });
        emitter.onError(ex -> {
            log.info("Agent SSE client error: {}, error: {}", clientId, ex.getMessage());
            emitters.remove(clientId);
        });

        return emitter;
    }

    /**
     * 向指定客户端发送消息并流式推送 Agent 执行进度。
     * <p>
     * Agent 循环的各阶段通过 SSE 事件实时推送给客户端。
     * 事件类型：thinking → memory → reply_chunk(s) → complete
     *
     * @param clientId 客户端唯一标识（需先通过 /connect 注册）
     * @param message  用户输入的消息文本
     * @return 操作结果提示，客户端未注册时返回 "客户端未连接"
     */
    @PostMapping("/send")
    public String sendToClient(@RequestParam String clientId, @RequestParam String message) {
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) {
            log.warn("Agent SSE send failed: client {} not connected", clientId);
            return "客户端未连接";
        }

        log.info("Agent SSE message from {}: {}", clientId, message);

        // 在后台线程执行 Agent 循环，避免阻塞 SSE 线程
        executor.submit(() -> {
            try {
                agentService.executeCycleWithEvents(message, eventJson -> {
                    try {
                        SseEmitter.SseEventBuilder sseEvent = SseEmitter.event()
                                .data(eventJson)
                                .name("agent_event")
                                .id(String.valueOf(System.currentTimeMillis()));
                        emitter.send(sseEvent);
                    } catch (IOException e) {
                        log.error("Failed to send SSE event to {}", clientId, e);
                        // Don't re-throw — let the agent complete its cycle
                    }
                });
            } catch (Exception e) {
                log.error("Agent cycle failed for client {}", clientId, e);
                try {
                    SseEmitter.SseEventBuilder errorEvent = SseEmitter.event()
                            .data(buildEventJson("error", "Agent 执行出错: " + e.getMessage()))
                            .name("agent_event")
                            .id(String.valueOf(System.currentTimeMillis()));
                    emitter.send(errorEvent);
                } catch (IOException ex) {
                    // Connection already closed
                }
            }
        });

        return "消息已提交";
    }

    /**
     * 获取当前连接的客户端状态。
     *
     * @return 包含客户端数量和客户端 ID 列表的 Map
     */
    @GetMapping("/clients")
    public Map<String, Object> getClients() {
        return Map.of(
                "clientCount", emitters.size(),
                "clients", emitters.keySet()
        );
    }

    /**
     * 构建统一的 SSE 事件 JSON 字符串。
     *
     * @param type    事件类型（connected、thinking、memory、reply_chunk、complete、error）
     * @param content 事件内容文本
     * @return JSON 格式的事件字符串
     */
    private String buildEventJson(String type, String content) {
        return String.format(
                "{\"type\":\"%s\",\"content\":%s,\"timestamp\":\"%s\"}",
                type,
                com.alibaba.fastjson.JSON.toJSONString(content),
                LocalDateTime.now().toString().substring(0, 19));
    }
}
