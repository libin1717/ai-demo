package com.libin.springai.aideepseek.agent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AgentSseController 集成测试，验证 SSE 端点的注册和连接管理。
 * <p>
 * 注意：SSE 端点返回 SseEmitter，MockMvc 不支持 SSE 流式读取，
 * 因此 send 端点的完整事件流需通过手动 curl 测试验证（见 Task 6）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AgentSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        // Clean .memory before each test
        Path memPath = Path.of(".memory");
        if (Files.exists(memPath)) {
            try (var stream = Files.walk(memPath)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        }
    }

    @Test
    void shouldReturnClientList() throws Exception {
        mockMvc.perform(get("/agent/sse/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientCount").value(0))
                .andExpect(jsonPath("$.clients").isArray());
    }

    @Test
    void shouldReturnErrorForUnconnectedClient() throws Exception {
        mockMvc.perform(post("/agent/sse/send")
                        .param("clientId", "nonexistent")
                        .param("message", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("客户端未连接"));
    }
}
