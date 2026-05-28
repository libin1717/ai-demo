package com.libin.springai.aideepseek.agent.service;

import com.alibaba.fastjson.JSON;
import com.libin.springai.aideepseek.agent.dto.AgentResponse;
import com.libin.springai.aideepseek.agent.dto.MemoryExtraction;
import com.libin.springai.aideepseek.agent.tool.AgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final int SKILL_THRESHOLD = 5;

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

    private static final String EXTRACT_PROMPT =
            "从以下对话中提取关键信息，输出纯 JSON（不要包含任何其他文字）：\n\n" +
            "{\n" +
            "  \"facts\": [\"关于项目的客观事实，如：使用的框架、端口号、包名、项目结构等\"],\n" +
            "  \"preferences\": [\"用户的编码偏好，如：喜欢用什么注解、命名风格、代码组织方式等\"],\n" +
            "  \"decisions\": [\"用户或 Agent 做出的技术决策，如：选择某方案而非另一方案及原因\"]\n" +
            "}\n\n" +
            "如果没有某类信息，对应数组留空。只输出JSON，不要输出其他内容。\n\n" +
            "对话内容：\n";

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

    public AgentResponse executeCycle(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setSkillTriggered(false);

        // 1. Search memories and skills
        Map<String, List<String>> memories = memoryStore.search(userMessage);
        Map<String, String> skills = skillManager.match(userMessage);

        response.setMemoriesUsed(memories.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

        for (String skillName : skills.keySet()) {
            skillManager.incrementScore(skillName);
        }

        // 2. Build system prompt
        String systemPrompt = buildSystemPrompt(memories, skills);

        // 3. Reset tool counter, call LLM with tool calling via ChatClient
        agentTools.getAndResetToolCallCount();

        String reply;
        try {
            ChatClient chatClient = chatClientBuilder.build();

            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .options(DeepSeekChatOptions.builder()
                            .model("deepseek-chat")
                            .temperature(0.0)
                            .build())
                    .call()
                    .chatResponse();

            reply = chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            reply = "抱歉，AI 服务暂时不可用，请稍后重试。错误: " + e.getMessage();
        }
        response.setReply(reply);

        int toolCallCount = agentTools.getAndResetToolCallCount();
        response.setToolCallCount(toolCallCount);

        // 4. Secondary LLM call: extract memories from conversation
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
        response.setNewMemoryCount(afterCount - beforeCount);

        // 5. Skill sedimentation: generate skill when tool calls >= threshold
        if (toolCallCount >= SKILL_THRESHOLD) {
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
                    // Skill generation failure doesn't block main flow
                }
            } catch (Exception e) {
                // Skill generation LLM call failure doesn't block main flow
            }
        }

        return response;
    }

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

    private int countMemoryEntries() {
        return memoryStore.getMemorySummary().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static class SkillGeneration {
        public String name;
        public String description;
        public List<String> triggers;
        public String content;

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getTriggers() { return triggers; }
        public String getContent() { return content; }
    }
}
