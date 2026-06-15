package com.libin.springai.aideepseek.agent.service;

import com.alibaba.fastjson.JSON;
import com.libin.springai.aideepseek.agent.dto.AgentResponse;
import com.libin.springai.aideepseek.agent.dto.MemoryExtraction;
import com.libin.springai.aideepseek.agent.tool.AgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 核心服务，封装完整的 Agent 执行循环。
 * <p>
 * 每次执行循环包含以下阶段：
 * <ol>
 *   <li>搜索相关记忆和技能</li>
 *   <li>构建包含上下文信息的系统提示词</li>
 *   <li>通过 ChatClient 调用 LLM（支持工具调用）</li>
 *   <li>从对话中提取关键信息并存入记忆</li>
 *   <li>当工具调用次数达到阈值时，沉淀生成为可复用技能</li>
 * </ol>
 * 灵感来源于 Nous Research Hermes Agent 的设计理念。
 */
@Slf4j
@Service
public class AgentService {

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private MemoryStore memoryStore;

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private AgentTools agentTools;

    /**
     * 触发技能生成的工具调用次数阈值
     */
    private static final int SKILL_THRESHOLD = 5;

    /**
     * Agent 基础角色定义，描述核心能力与行为约束
     */
    private static final String AGENT_ROLE =
            "你是一个具有持久记忆和自我进化能力的 AI Agent（灵感来自 Nous Research Hermes Agent）。\n\n" +
                    "核心能力：\n" +
                    "- 你可以使用工具来读写文件、搜索代码、查看目录结构\n" +
                    "- 每次对话后，系统会自动从对话中提取关键信息存入记忆\n" +
                    "- 当任务足够复杂时，系统会自动将你的工作模式沉淀为可复用的技能\n" +
                    "- 后续对话会自动加载相关记忆和技能，让你越用越聪明\n\n" +
                    "注意事项：\n" +
                    "- 所有文件操作限定在 .sandbox/ 目录内\n" +
                    "- 创建新文件前先查看目录结构，了解现有文件\n" +
                    "- 生成的代码要完整、可运行\n" +
                    "- 用中文回复用户";

    /**
     * 记忆提取提示词模板，要求 LLM 输出纯 JSON
     */
    private static final String EXTRACT_PROMPT =
            "从以下对话中提取关键信息，输出纯 JSON（不要包含任何其他文字）：\n\n" +
                    "{\n" +
                    "  \"facts\": [\"关于项目的客观事实，如：使用的框架、端口号、包名、项目结构等\"],\n" +
                    "  \"preferences\": [\"用户的编码偏好，如：喜欢用什么注解、命名风格、代码组织方式等\"],\n" +
                    "  \"decisions\": [\"用户或 Agent 做出的技术决策，如：选择某方案而非另一方案及原因\"]\n" +
                    "}\n\n" +
                    "如果没有某类信息，对应数组留空。只输出JSON，不要输出其他内容。\n\n" +
                    "对话内容：\n";

    /**
     * 技能文档生成提示词模板，要求 LLM 输出包含技能元数据的纯 JSON
     */
    private static final String SKILL_GENERATE_PROMPT =
            "基于以下对话，总结出一份可复用的技能文档。\n\n" +
                    "技能文档格式要求：\n" +
                    "1. 技能名称（kebab-case，如 spring-controller-pattern）\n" +
                    "2. 一句话描述\n" +
                    "3. 触发关键词（逗号分隔的列表，用于后续检索匹配）\n" +
                    "4. 技能正文（Markdown 格式，包含具体的模式、模板、注意事项）\n\n" +
                    "输出纯 JSON（不要包含任何其他文字）：\n" +
                    "{\n" +
                    "  \"name\": \"技能名称\",\n" +
                    "  \"description\": \"一句话描述\",\n" +
                    "  \"triggers\": [\"关键词1\", \"关键词2\"],\n" +
                    "  \"content\": \"技能正文（Markdown）\"\n" +
                    "}\n\n" +
                    "对话内容：\n";

    /**
     * 关键词提取提示词，要求 LLM 从用户消息中提取搜索关键词
     */
    private static final String KEYWORD_EXTRACT_PROMPT =
            "从以下用户消息中提取3-5个用于搜索技术文档的关键词（名词、术语、框架名、注解名、技术概念），"
                    + "用逗号分隔，只输出关键词，不要输出任何其他内容。\n\n"
                    + "用户消息：";

    /**
     * 执行一次完整的 Agent 对话循环。
     * <p>
     * 流程：记忆/技能搜索 → 构建提示词 → LLM 调用（含工具）→ 记忆提取 → 技能沉淀（条件触发）。
     * 记忆提取和技能生成的失败不会影响主流程的回复返回。
     *
     * @param userMessage 用户输入的文本消息
     * @return 包含回复、工具调用统计、记忆使用和技能生成状态的汇总结果
     */
    public AgentResponse executeCycle(String userMessage) {
        return executeCycleWithEvents(userMessage, null);
    }

    /**
     * 执行一次完整的 Agent 对话循环，并通过回调函数在各阶段推送 SSE 事件。
     * <p>
     * 与 {@link #executeCycle(String)} 流程相同，但在每个阶段通过 eventCallback
     * 发送进度事件（thinking → memory → reply_chunk → complete）。
     * 记忆提取和技能生成阶段不额外发送事件以简化流。
     *
     * @param userMessage   用户输入的文本消息
     * @param eventCallback 事件回调函数，接收事件 JSON 字符串；为 null 时静默跳过
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
        }

        String reply = replyBuilder.toString();

        // Send reply in chunks (split by sentence boundaries for pseudo-streaming)
        String[] sentences = reply.split("(?<=[。.！!？?\n])");
        for (String sentence : sentences) {
            if (!sentence.isBlank()) {
                sendEvent(eventCallback, "reply_chunk", sentence.trim());
            }
        }

        response.setReply(reply);

        int toolCallCount = agentTools.getAndResetToolCallCount();
        response.setToolCallCount(toolCallCount);

        // 4. Extract memories (same logic as executeCycle)
        String conversation = "用户: " + userMessage + "\n\nAI: " + reply;
        String extractResult;
        try {
            extractResult = deepSeekChatModel.call(EXTRACT_PROMPT + conversation);
        } catch (Exception e) {
            extractResult = "{\"facts\":[],\"preferences\":[],\"decisions\":[]}";
        }
        log.info("extractResult:{}", extractResult);

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
     * @param callback 事件回调（为 null 时静默跳过）
     * @param type     事件类型（thinking、memory、reply_chunk、complete 等）
     * @param content  事件内容文本
     */
    private void sendEvent(java.util.function.Consumer<String> callback, String type, String content) {
        if (callback == null) return;
        try {
            String escaped = JSON.toJSONString(content);
            // JSON.toJSONString wraps result in quotes, remove them since we're embedding in a format string
            if (escaped.startsWith("\"") && escaped.endsWith("\"")) {
                escaped = escaped.substring(1, escaped.length() - 1);
            }
            String eventJson = String.format(
                    "{\"type\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                    type,
                    escaped,
                    java.time.LocalDateTime.now().toString().substring(0, 19));
            callback.accept(eventJson);
        } catch (Exception e) {
            log.warn("Failed to send SSE event: type={}", type, e);
        }
    }

    /**
     * 构建发送给 LLM 的完整系统提示词。
     * <p>
     * 拼接基础角色定义、匹配的历史记忆和匹配的技能文档，
     * 在末尾追加指示让 LLM 优先使用记忆和技能中的信息。
     *
     * @param memories 匹配的历史记忆（文件名 → 内容行列表）
     * @param skills   匹配的技能文档（技能名 → 正文内容）
     * @return 完整的系统提示词字符串
     */
    private String buildSystemPrompt(Map<String, List<String>> memories, Map<String, String> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append(AGENT_ROLE);

        if (!memories.isEmpty()) {
            sb.append("\n\n---\n## 相关历史记忆\n\n");
            for (Map.Entry<String, List<String>> entry : memories.entrySet()) {
                sb.append("来自 ").append(entry.getKey()).append(":\n");
                for (String line : entry.getValue()) {
                    sb.append(line).append("\n");
                }
            }
        }

        if (!skills.isEmpty()) {
            sb.append("\n---\n## 相关技能\n\n");
            for (Map.Entry<String, String> entry : skills.entrySet()) {
                sb.append("### ").append(entry.getKey()).append("\n\n");
                sb.append(entry.getValue()).append("\n\n");
            }
        }

        sb.append("\n---\n请根据以上上下文回答用户的问题。如果记忆中有相关信息，优先使用。如果技能中有相关模式，严格遵循。");
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的记忆提取 JSON 字符串。
     * <p>
     * 自动处理被 markdown 代码块包裹的 JSON，解析失败时返回空的 MemoryExtraction。
     *
     * @param raw LLM 返回的原始文本
     * @return 解析后的 MemoryExtraction 对象，解析失败时所有字段为空列表
     */
    MemoryExtraction parseExtraction(String raw) {
        if (raw == null || raw.isBlank()) {
            return new MemoryExtraction(List.of(), List.of(), List.of());
        }
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf("\n") + 1);
                if (json.endsWith("```")) {
                    json = json.substring(0, json.lastIndexOf("```")).trim();
                }
            }
            return JSON.parseObject(json, MemoryExtraction.class);
        } catch (Exception e) {
            return new MemoryExtraction(List.of(), List.of(), List.of());
        }
    }

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
                    new Prompt(KEYWORD_EXTRACT_PROMPT + userMessage,
                            DeepSeekChatOptions.builder()
                                    .model("deepseek-chat")
                                    .temperature(0.0)
                                    .build()))
                    .getResult().getOutput().getText();
            if (result == null || result.isBlank()) {
                log.warn("LLM returned empty keywords, falling back to original message");
                return List.of(userMessage.trim());
            }
            // 解析逗号分隔的关键词，处理中英文逗号，去重
            String cleaned = result.trim().replace("，", ",");
            List<String> keywords = Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
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

    /**
     * 解析 LLM 返回的技能生成 JSON 字符串。
     * <p>
     * 自动处理被 markdown 代码块包裹的 JSON，解析失败时返回 null。
     *
     * @param raw LLM 返回的原始文本
     * @return 解析后的 SkillGeneration 对象，解析失败时返回 null
     */
    private SkillGeneration parseSkillGeneration(String raw) {
        try {
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf("\n") + 1);
                if (json.endsWith("```")) {
                    json = json.substring(0, json.lastIndexOf("```")).trim();
                }
            }
            return JSON.parseObject(json, SkillGeneration.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 统计当前所有记忆文件中的总条目数。
     *
     * @return 所有记忆文件的总条目数
     */
    private int countMemoryEntries() {
        return memoryStore.getMemorySummary().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 技能生成结果的内部类，用作 LLM 技能生成提示的 JSON 反序列化目标。
     */
    private static class SkillGeneration {
        /**
         * 技能名称（kebab-case 格式）
         */
        public String name;
        /**
         * 技能的一句话描述
         */
        public String description;
        /**
         * 触发关键词列表
         */
        public List<String> triggers;
        /**
         * 技能正文内容（Markdown 格式）
         */
        public String content;

        /**
         * 获取技能名称
         */
        public String getName() {
            return name;
        }

        /**
         * 获取技能描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 获取触发关键词列表
         */
        public List<String> getTriggers() {
            return triggers;
        }

        /**
         * 获取技能正文内容
         */
        public String getContent() {
            return content;
        }
    }
}
