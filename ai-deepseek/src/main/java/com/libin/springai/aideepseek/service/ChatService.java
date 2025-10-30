package com.libin.springai.aideepseek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libin.springai.aideepseek.dto.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * {@code @User} libin
 * {@code @Data} 2025/10/21
 * {@code @Version} 1.0
 * {@code @Description}
 */
@Service
public class ChatService {

    @Value("${spring.ai.deepseek.baseUrl:https://api.deepseek.com}")
    private String deepseekUrl;

    public Flux<ServerSentEvent<String>> streamWithDeepSeek(ChatRequest request) {
        // 1. 构建WebClient实例
        WebClient client = WebClient.builder()
                .baseUrl(deepseekUrl) // 从配置读取
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "YOUR_API_KEY")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .build();

        // 2. 发送POST请求，并声明响应体为Flux<String>（即SSE流）
        return client.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                // 3. 处理DeepSeek返回的原始数据
                .flatMap(data -> {
                    // 判断流是否结束:cite[10]
                    if ("[DONE]".equals(data)) {
                        return Flux.just(ServerSentEvent.<String>builder().event("end").data(data).build());
                    }
                    // 解析JSON，提取Delta Content:cite[5]:cite[10]
                    // 这里简化为直接返回原始数据，实际应用中需要解析JSON
                    String content = parseContentFromJson(data);
                    if (content != null) {
                        // 4. 将解析出的内容包装成SSE事件
                        return Flux.just(ServerSentEvent.<String>builder().data(content).build());
                    }
                    return Flux.empty();
                })
                .onErrorResume(throwable -> {
                    // 错误处理：将异常信息以SSE形式返回给前端
                    return Flux.just(ServerSentEvent.<String>builder().event("error").data(throwable.getMessage()).build());
                });
    }


    /**
     * @param jsonLine 参数
     * @return 辅助方法：从DeepSeek返回的JSON行中解析出"delta.content"
     */
    private String parseContentFromJson(String jsonLine) {
        // 使用Jackson或Gson等库解析JSON
        // 示例逻辑，具体字段结构请参考DeepSeek官方API文档
        try {
            // 伪代码：解析出content字段
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(jsonLine);
            return node.path("choices").get(0).path("delta").path("content").asText();
        } catch (Exception e) {
            return null;
        }
    }
}
