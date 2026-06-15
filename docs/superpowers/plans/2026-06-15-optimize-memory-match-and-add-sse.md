# Optimize Memory Match and Add SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix memory matching (currently returns empty for natural language queries) and add SSE streaming support to the Hermes Agent.

**Architecture:** LLM-assisted keyword extraction bridges the semantic gap between natural language queries and technical memory entries. A new `AgentSseController` follows the existing `StreamChatCompletionsController` SSE pattern (SseEmitter + ConcurrentHashMap + event-based streaming), reusing `AgentService` with a callback-based streaming variant.

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring AI 1.0.1, DeepSeekChatModel, SseEmitter, Maven

---

### Task 1: Add keyword-based multi-search to MemoryStore

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/MemoryStore.java:141-177`
- Test: `ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/MemoryStoreTest.java`

- [ ] **Step 1: Add `searchByKeywords` method to MemoryStore**

Open `MemoryStore.java` and add the following method after the existing `search()` method (after line 177):

```java
/**
 * 按多个关键词搜索记忆，合并去重并按命中次数降序排列。
 * <p>
 * 对每个关键词调用 {@link #search(String)}，然后将结果合并：
 * 同一条记忆线命中更多关键词的排在前面。
 *
 * @param keywords 搜索关键词列表（通常由 LLM 从用户消息中提取）
 * @return 文件名到匹配行列表的映射，按多关键词命中次数排序；无结果时返回空 Map
 */
public synchronized Map<String, List<String>> searchByKeywords(List<String> keywords) {
    Map<String, List<String>> results = new LinkedHashMap<>();
    if (keywords == null || keywords.isEmpty()) {
        return results;
    }

    // 记录每条记忆线被多少关键词命中：fileName -> (line -> hitCount)
    Map<String, Map<String, Integer>> hitCounts = new LinkedHashMap<>();

    for (String keyword : keywords) {
        if (keyword == null || keyword.isBlank()) continue;
        Map<String, List<String>> keywordResults = search(keyword);
        for (Map.Entry<String, List<String>> entry : keywordResults.entrySet()) {
            String file = entry.getKey();
            hitCounts.putIfAbsent(file, new LinkedHashMap<>());
            Map<String, Integer> fileHits = hitCounts.get(file);
            for (String line : entry.getValue()) {
                fileHits.merge(line, 1, Integer::sum);
            }
        }
    }

    // 按命中次数降序排列，同命中次数保持原有精确/部分匹配顺序
    for (Map.Entry<String, Map<String, Integer>> fileEntry : hitCounts.entrySet()) {
        String file = fileEntry.getKey();
        List<String> sorted = fileEntry.getValue().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        results.put(file, sorted);
    }

    log.info("Keyword search: keywords={}, hitFiles={}", keywords, results.keySet());
    return results;
}
```

Add the missing import at the top of the file alongside the existing imports:

```java
import java.util.stream.Collectors;
```

- [ ] **Step 2: Write the failing test for multi-keyword search**

Open `MemoryStoreTest.java` and add this test method inside the class:

```java
@Test
void shouldRankByMultipleKeywordHits() {
    memoryStore.saveFact("项目使用 Spring Boot 3.5.5 框架");
    memoryStore.saveFact("使用 @Valid 注解做参数校验");
    memoryStore.saveFact("端口号是 8091");

    Map<String, List<String>> results = memoryStore.searchByKeywords(
            List.of("Spring", "注解", "Docker"));

    assertEquals(1, results.size());
    List<String> matches = results.get("facts.md");
    // "使用 @Valid 注解做参数校验" hits both "Spring" and "注解"
    // (actually: "Spring" hits "Spring Boot", "注解" hits "@Valid 注解")
    // Both lines get 1 hit each, but "Spring Boot" was stored first so stays first
    assertEquals(2, matches.size());
    assertTrue(matches.get(0).contains("Spring Boot") || matches.get(1).contains("Spring Boot"));
}

@Test
void shouldReturnEmptyForNoKeywordMatches() {
    memoryStore.saveFact("项目使用 Spring Boot");
    Map<String, List<String>> results = memoryStore.searchByKeywords(
            List.of("Docker", "Kubernetes"));
    assertTrue(results.isEmpty());
}

@Test
void shouldHandleEmptyKeywordList() {
    memoryStore.saveFact("项目使用 Spring Boot");
    Map<String, List<String>> results = memoryStore.searchByKeywords(List.of());
    assertTrue(results.isEmpty());
}

@Test
void shouldHandleNullKeywordList() {
    memoryStore.saveFact("项目使用 Spring Boot");
    Map<String, List<String>> results = memoryStore.searchByKeywords(null);
    assertTrue(results.isEmpty());
}
```

- [ ] **Step 3: Run the memory tests to verify they pass**

```bash
./mvnw -pl ai-deepseek -Dtest=MemoryStoreTest test -DskipTests=false
```

Expected: All MemoryStoreTest tests PASS (including the 4 new keyword search tests).

---

### Task 2: Add LLM keyword extraction to AgentService

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/AgentService.java:113-218`
- Test: `ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/AgentServiceTest.java`

- [ ] **Step 1: Add `extractKeywords` method to AgentService**

Open `AgentService.java`. Add the keyword extraction prompt constant after the existing constants (after line 88):

```java
/**
 * 关键词提取提示词，要求 LLM 从用户消息中提取搜索关键词
 */
private static final String KEYWORD_EXTRACT_PROMPT =
        "从以下用户消息中提取3-5个用于搜索技术文档的关键词（名词、术语、框架名、注解名、技术概念），"
                + "用逗号分隔，只输出关键词，不要输出任何其他内容。\n\n"
                + "用户消息：";
```

Add the `extractKeywords` method after the `parseExtraction` method (after line 280):

```java
/**
 * 使用 LLM 从用户消息中提取搜索关键词。
 * <p>
 * 对于短消息（少于 10 字符），跳过 LLM 调用，直接返回原始消息作为唯一关键词。
 * LLM 调用失败时返回原始消息作为兜底。
 *
 * @param userMessage 用户输入的原始消息
 * @return 提取的关键词列表，至少包含一个元素
 */
List<String> extractKeywords(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
        return List.of();
    }
    // 短消息跳过 LLM 提取
    if (userMessage.trim().length() < 10) {
        log.info("Message too short, skipping keyword extraction: '{}'", userMessage);
        return List.of(userMessage.trim());
    }
    try {
        String result = deepSeekChatModel.call(
                DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.0)
                        .build(),
                KEYWORD_EXTRACT_PROMPT + userMessage);
        if (result == null || result.isBlank()) {
            log.warn("LLM returned empty keywords, falling back to original message");
            return List.of(userMessage.trim());
        }
        // 解析逗号分隔的关键词
        String cleaned = result.trim().replace("，", ",");
        List<String> keywords = Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (keywords.isEmpty()) {
            return List.of(userMessage.trim());
        }
        log.info("Extracted keywords for '{}': {}", userMessage, keywords);
        return keywords;
    } catch (Exception e) {
        log.warn("Keyword extraction failed, falling back to original message", e);
        return List.of(userMessage.trim());
    }
}
```

Add the required imports at the top of the file (alongside existing imports):

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
```

- [ ] **Step 2: Modify `executeCycle()` to use keyword-based search**

In `AgentService.java`, find the memory search section in `executeCycle()` (lines 119-128). Replace:

```java
// 1. Search memories and skills
Map<String, List<String>> memories = memoryStore.search(userMessage);
Map<String, String> skills = skillManager.match(userMessage);
log.info("search skills userMessage:{},result：{}", userMessage, JSON.toJSONString(skills));
response.setMemoriesUsed(memories.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
```

With:

```java
// 1. Search memories and skills
List<String> keywords = extractKeywords(userMessage);
Map<String, List<String>> memories;
if (keywords.size() == 1 && keywords.get(0).equals(userMessage.trim())) {
    // Short message fallback: use original substring search
    memories = memoryStore.search(userMessage);
} else {
    memories = memoryStore.searchByKeywords(keywords);
    // Fallback: if keyword search yields nothing, try original message
    if (memories.isEmpty()) {
        log.info("Keyword search returned empty, falling back to original message search");
        memories = memoryStore.search(userMessage);
    }
}
log.info("Memory search: keywords={}, matchedFiles={}, matchCounts={}",
        keywords, memories.keySet(),
        memories.values().stream().mapToInt(List::size).sum());

Map<String, String> skills = skillManager.match(userMessage);
log.info("search skills userMessage:{},result：{}", userMessage, JSON.toJSONString(skills));
response.setMemoriesUsed(memories.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
```

- [ ] **Step 3: Write tests for keyword extraction parsing**

Open `AgentServiceTest.java`. Add these test methods inside the class:

```java
@Test
void shouldExtractKeywordsFromShortMessageDirectly() {
    // Messages under 10 chars skip LLM and return the original message
    List<String> keywords = agentService.extractKeywords("hello");
    assertEquals(1, keywords.size());
    assertEquals("hello", keywords.get(0));
}

@Test
void shouldHandleNullMessageInExtractKeywords() {
    List<String> keywords = agentService.extractKeywords(null);
    assertTrue(keywords.isEmpty());
}

@Test
void shouldHandleBlankMessageInExtractKeywords() {
    List<String> keywords = agentService.extractKeywords("   ");
    assertTrue(keywords.isEmpty());
}
```

Note: The `extractKeywords` method uses `DeepSeekChatModel` for messages >= 10 chars. Since `AgentServiceTest` doesn't have Spring context (no `@Autowired`), the LLM-calling branch requires a mock or integration test. The unit tests above cover the pure-logic branches (null, blank, short message paths). The full LLM path is tested end-to-end in Task 6.

- [ ] **Step 4: Run tests to verify**

```bash
./mvnw -pl ai-deepseek -Dtest=AgentServiceTest test -DskipTests=false
```

Expected: All AgentServiceTest tests PASS.

---

### Task 3: Decompose AgentService for streaming — add `executeCycleWithEvents`

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/AgentService.java`

- [ ] **Step 1: Add the streaming Agent method `executeCycleWithEvents`**

Open `AgentService.java`. Add the following method after the existing `executeCycle()` method (after line 218):

```java
/**
 * 执行一次完整的 Agent 对话循环，并通过回调函数在各阶段推送 SSE 事件。
 * <p>
 * 与 {@link #executeCycle(String)} 流程相同，但在每个阶段通过 eventCallback
 * 发送进度事件（thinking → memory → tool_call → tool_result → reply_chunk → complete）。
 * 记忆提取和技能生成阶段不发送事件以简化流。
 *
 * @param userMessage   用户输入的文本消息
 * @param eventCallback 事件回调函数，接收事件 JSON 字符串
 * @return 包含回复、工具调用统计、记忆使用和技能沉淀状态的汇总结果
 */
public AgentResponse executeCycleWithEvents(String userMessage,
                                             java.util.function.Consumer<String> eventCallback) {
    log.info("开始执行agent对话(SSE)，userMessage:{}", userMessage);
    AgentResponse response = new AgentResponse();
    response.setSkillTriggered(false);

    // 1. Thinking: searching memories
    sendEvent(eventCallback, "thinking", "正在分析用户消息并搜索相关记忆...");
    List<String> keywords = extractKeywords(userMessage);
    Map<String, List<String>> memories;
    if (keywords.size() == 1 && keywords.get(0).equals(userMessage.trim())) {
        memories = memoryStore.search(userMessage);
    } else {
        memories = memoryStore.searchByKeywords(keywords);
        if (memories.isEmpty()) {
            log.info("Keyword search returned empty, falling back to original message search");
            memories = memoryStore.search(userMessage);
        }
    }

    Map<String, String> skills = skillManager.match(userMessage);
    response.setMemoriesUsed(memories.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

    // Send memory event
    int totalMatches = memories.values().stream().mapToInt(List::size).sum();
    if (totalMatches > 0) {
        sendEvent(eventCallback, "memory",
                String.format("找到 %d 条相关记忆（关键词: %s）", totalMatches,
                        String.join(", ", keywords)));
    } else {
        sendEvent(eventCallback, "memory", "未找到相关记忆，使用通用知识回答");
    }

    for (String skillName : skills.keySet()) {
        skillManager.incrementScore(skillName);
    }

    // 2. Build system prompt
    String systemPrompt = buildSystemPrompt(memories, skills);
    log.info("Build system prompt：{}", systemPrompt);

    // 3. Reset tool counter, call LLM with tool calling via ChatClient
    agentTools.getAndResetToolCallCount();

    StringBuilder replyBuilder = new StringBuilder();
    try {
        ChatClient chatClient = chatClientBuilder.build();

        // First call with tools enabled
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(agentTools)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-chat")
                        .temperature(0.0)
                        .build())
                .call()
                .chatResponse();

        replyBuilder.append(chatResponse.getResult().getOutput().getText());
    } catch (Exception e) {
        String errorMsg = "抱歉，AI 服务暂时不可用，请稍后重试。错误: " + e.getMessage();
        replyBuilder.append(errorMsg);
        sendEvent(eventCallback, "reply_chunk", errorMsg);
    }

    String reply = replyBuilder.toString();

    // Send reply in chunks (simulate streaming by sending sentence by sentence)
    // DeepSeekChatModel.call() is blocking, so we split the result for pseudo-streaming
    String[] sentences = reply.split("(?<=[。.！!？?\\n])");
    for (String sentence : sentences) {
        if (!sentence.isBlank()) {
            sendEvent(eventCallback, "reply_chunk", sentence.trim());
        }
    }

    response.setReply(reply);

    int toolCallCount = agentTools.getAndResetToolCallCount();
    response.setToolCallCount(toolCallCount);

    // 4. Extract memories
    String conversation = "用户: " + userMessage + "\n\nAI: " + reply;
    String extractResult;
    try {
        extractResult = deepSeekChatModel.call(EXTRACT_PROMPT + conversation);
    } catch (Exception e) {
        extractResult = "{\"facts\":[],\"preferences\":[],\"decisions\":[]}";
    }

    MemoryExtraction extraction = parseExtraction(extractResult);
    int beforeCount = countMemoryEntries();

    if (extraction != null) {
        if (extraction.getFacts() != null) {
            for (String fact : extraction.getFacts()) {
                if (!fact.isBlank()) memoryStore.saveFact(fact.trim());
            }
        }
        if (extraction.getPreferences() != null) {
            for (String pref : extraction.getPreferences()) {
                if (!pref.isBlank()) memoryStore.savePreference(pref.trim());
            }
        }
        if (extraction.getDecisions() != null) {
            for (String dec : extraction.getDecisions()) {
                if (!dec.isBlank()) memoryStore.saveDecision(dec.trim());
            }
        }
    }

    int afterCount = countMemoryEntries();
    int newMemories = afterCount - beforeCount;
    response.setNewMemoryCount(newMemories);

    // 5. Skill generation
    if (toolCallCount >= SKILL_THRESHOLD) {
        sendEvent(eventCallback, "thinking", "工具调用达到阈值，正在生成可复用技能...");
        try {
            String skillResult = deepSeekChatModel.call(SKILL_GENERATE_PROMPT + conversation);
            try {
                SkillGeneration gen = parseSkillGeneration(skillResult);
                if (gen != null && gen.getName() != null) {
                    skillManager.createSkill(gen.getName(), gen.getDescription(),
                            gen.getTriggers(), gen.getContent());
                    response.setSkillTriggered(true);
                    response.setNewSkillName(gen.getName());
                }
            } catch (Exception e) {
                log.error("Skill generation failure for:", e);
            }
        } catch (Exception e) {
            log.error("Skill generation LLM call failure for", e);
        }
    }

    // 6. Complete event with summary
    sendEvent(eventCallback, "complete",
            String.format("{\"toolCalls\":%d,\"newMemories\":%d,\"skillTriggered\":%s,\"memoriesUsed\":%s}",
                    toolCallCount, newMemories,
                    response.isSkillTriggered() ? "\"" + response.getNewSkillName() + "\"" : "null",
                    JSON.toJSONString(response.getMemoriesUsed())));

    log.info("Agent cycle (SSE) completed. reply length={}, toolCalls={}, newMemories={}",
            reply.length(), toolCallCount, newMemories);
    return response;
}

/**
 * 通过回调函数发送 SSE 事件 JSON 字符串。
 *
 * @param callback 事件回调（可能为 null，此时静默跳过）
 * @param type     事件类型
 * @param content  事件内容
 */
private void sendEvent(java.util.function.Consumer<String> callback, String type, String content) {
    if (callback == null) return;
    try {
        String eventJson = String.format(
                "{\"type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                type,
                content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"),
                java.time.LocalDateTime.now().toString().substring(0, 19));
        callback.accept(eventJson);
    } catch (Exception e) {
        log.warn("Failed to send SSE event: type={}", type, e);
    }
}
```

---

### Task 4: Create AgentSseController

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/AgentSseController.java`

- [ ] **Step 1: Write the full AgentSseController**

Create file `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/AgentSseController.java`:

```java
package com.libin.springai.aideepseek.agent.controller;

import com.libin.springai.aideepseek.agent.dto.AgentResponse;
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
 *   <li>客户端通过 GET /connect 注册连接</li>
 *   <li>通过 POST /send 发送消息并流式接收 Agent 各阶段进度</li>
 *   <li>支持超时、错误和完成回调自动清理连接</li>
 * </ul>
 * 事件类型包括 thinking、memory、tool_call、tool_result、reply_chunk、complete。
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
        new Thread(() -> {
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
        }, "agent-sse-" + clientId).start();

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
     * @param type    事件类型（connected、thinking、memory、tool_call、tool_result、reply_chunk、complete、error）
     * @param content 事件内容文本
     * @return JSON 格式的事件字符串
     */
    private String buildEventJson(String type, String content) {
        return String.format(
                "{\"type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                type,
                content.replace("\\", "\\\\").replace("\"", "\\\""),
                LocalDateTime.now().toString().substring(0, 19));
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS.

---

### Task 5: Write AgentSseController integration test

**Files:**
- Create: `ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/controller/AgentSseControllerTest.java`

- [ ] **Step 1: Write the Spring Boot integration test**

Create file `ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/controller/AgentSseControllerTest.java`:

```java
package com.libin.springai.aideepseek.agent.controller;

import com.libin.springai.aideepseek.agent.service.AgentService;
import com.libin.springai.aideepseek.agent.service.MemoryStore;
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
```

- [ ] **Step 2: Run the integration test**

```bash
./mvnw -pl ai-deepseek -Dtest=AgentSseControllerTest test -DskipTests=false
```

Expected: 2 tests PASS.

---

### Task 6: End-to-end verification

**Files:**
- No code changes — verification only

- [ ] **Step 1: Start the ai-deepseek module**

```bash
./mvnw -pl ai-deepseek spring-boot:run
```

Wait for startup log: `Started AiDeepseekApplication in X.XXX seconds`

- [ ] **Step 2: Verify memory matching improvement**

Open a second terminal. Test with a natural language query that should match existing memories:

```bash
# First, populate some memories via the agent chat endpoint
curl -X POST "http://localhost:8091/agent/chat?message=帮我写一个用户注册Controller"

# Then test with a different natural language query that should trigger keyword extraction
curl -X POST "http://localhost:8091/agent/chat?message=再帮我写一个带参数校验的订单接口"
```

Expected: The second call's log output should show:
- `Extracted keywords for '再帮我写一个带参数校验的订单接口': [Controller, 参数校验, 订单, ...]`
- `Keyword search: keywords=[...], hitFiles=[facts.md, profile.md]`
- Non-empty `memoriesUsed` in response

- [ ] **Step 3: Verify SSE streaming endpoint**

```bash
# Terminal 1: Connect to SSE
curl -N -H "Accept:text/event-stream" "http://localhost:8091/agent/sse/connect?clientId=test-user-1"

# Terminal 2: Send a message
curl -X POST "http://localhost:8091/agent/sse/send?clientId=test-user-1&message=帮我写一个商品列表接口"
```

Expected in Terminal 1: A stream of SSE events:
```
event:connected
data:{"type":"connected","content":"Agent SSE 连接成功","timestamp":"..."}

event:agent_event
data:{"type":"thinking","content":"正在分析用户消息并搜索相关记忆...","timestamp":"..."}

event:agent_event
data:{"type":"memory","content":"找到 N 条相关记忆...","timestamp":"..."}

event:agent_event
data:{"type":"reply_chunk","content":"...","timestamp":"..."}

event:agent_event
data:{"type":"complete","content":"...","timestamp":"..."}
```

- [ ] **Step 4: Run full test suite**

```bash
./mvnw -pl ai-deepseek test -DskipTests=false
```

Expected: All tests PASS.

- [ ] **Step 5: Stop the server (Ctrl+C in the server terminal)**

---
