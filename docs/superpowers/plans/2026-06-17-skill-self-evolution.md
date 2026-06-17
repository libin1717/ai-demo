# Skill Self-Evolution 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Hermes Agent 框架上构建**全自动、用户无感**的 skill 自我进化引擎——追踪每次 skill 使用、自动评分、自动触发富化并质量门禁校验后自动应用、版本管理、退化自动回滚。不新增任何对外 REST API。

**Architecture:** 新增 `SkillUsageTracker`（JSONL 文件追踪）和 `SkillEvolutionService`（评分+富化+质量门禁+退化回滚）两个 Service，扩展 `SkillManager`（版本管理+去重+元数据更新），在 `AgentService.executeCycle` 中注入追踪和进化调用点。所有进化逻辑内置在 Agent 执行循环中，纯后台运行。**不修改 HermesAgentController**。

**Tech Stack:** Java 17, Spring Boot 3.5.5, Spring AI 1.0.1, Lombok, FastJSON, DeepSeek Chat API

---

## 文件结构总览

```
ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/
├── dto/
│   ├── SkillInfo.java              [MODIFY] +effectiveness +usageCount +lastUsed +successRate
│   ├── AgentResponse.java          [MODIFY] +skillsMatched: List<String>
│   └── SkillUsageRecord.java       [CREATE]  JSONL 记录 DTO
├── service/
│   ├── SkillUsageTracker.java      [CREATE]  使用追踪服务（纯内部，无 API 暴露）
│   ├── SkillEvolutionService.java  [CREATE]  评分+富化+质量门禁+自动回滚核心服务
│   ├── SkillManager.java           [MODIFY]  +updateSkill +checkDuplicate +version管理 +frontmatter扩展
│   └── AgentService.java           [MODIFY]  注入 tracker + evolution 调用点
└── controller/
    └── HermesAgentController.java  [NO CHANGE] 不新增任何端点

ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/
├── SkillUsageTrackerTest.java      [CREATE]
├── SkillEvolutionServiceTest.java  [CREATE]
└── SkillManagerTest.java           [MODIFY] 扩展版本管理和 evolution 字段测试
```

---

### Task 1: 扩展 SkillInfo DTO — 新增进化相关字段

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/SkillInfo.java`

- [ ] **Step 1: 添加 4 个新字段**

在现有字段列表末尾（`created` 字段之后，结束大括号之前）添加：

```java
// ===== 进化相关新字段 =====

/** 综合有效性评分 (0-100)，由 SkillEvolutionService 每次使用后自动计算 */
private int effectiveness;

/** 技能被匹配使用的总次数 */
private int usageCount;

/** 最后一次被匹配使用的时间（ISO 8601 格式），从未使用过时为 null */
private String lastUsed;

/** 使用成功率 (0-100)：成功次数 / 总匹配次数 × 100 */
private int successRate;
```

注意：`@AllArgsConstructor` 和 `@NoArgsConstructor` 由 Lombok 自动生成，添加字段后自动覆盖新字段，无需手动修改构造器。

- [ ] **Step 2: 验证编译**

```bash
cd "D:\10.code\10.springAi\ai-demo"
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 2: 新建 SkillUsageRecord DTO

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/SkillUsageRecord.java`

- [ ] **Step 1: 创建 DTO 文件**

```java
package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 使用追踪记录 DTO，对应 JSONL 文件中的每一行。
 * 记录类型包括：match（匹配）、outcome（结果）、created（创建）。
 * 纯内部使用，不暴露于 API 响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillUsageRecord {

    /** ISO 8601 时间戳 */
    private String timestamp;

    /** 记录类型: "match" | "outcome" | "created" */
    private String type;

    /** 技能名称 */
    private String skillName;

    /** 匹配时命中的触发关键词（仅 match/created 类型） */
    private List<String> triggers;

    /** 用户消息摘要，截取前 200 字符（仅 match 类型） */
    private String userMessageDigest;

    /** 本次对话的工具调用次数 */
    private int toolCallCount;

    /** 本次对话新增的记忆条目数 */
    private int newMemoryCount;

    /** 执行结果: "success" | "failure"（仅 outcome 类型） */
    private String result;

    /** 错误信息，截取前 200 字符（仅 outcome 类型且 result=failure） */
    private String errorMessage;

    /** 技能描述（仅 created 类型） */
    private String description;
}
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 3: 扩展 AgentResponse DTO

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/AgentResponse.java`

- [ ] **Step 1: 添加 skillsMatched 字段**

在现有 `newMemoryCount` 字段后添加：

```java
/** 本轮对话中匹配到的技能名称列表，无匹配时为空列表。用于内部追踪传递 */
private List<String> skillsMatched;
```

完整修改后的字段区域：

```java
/** AI 生成的回复文本 */
private String reply;

/** 本次对话中工具调用的总次数 */
private int toolCallCount;

/** 本次对话中使用的记忆统计，key 为记忆文件名，value 为匹配的记忆条数 */
private Map<String, Integer> memoriesUsed;

/** 本次对话是否触发了新技能的生成（工具调用次数达到阈值时触发） */
private boolean skillTriggered;

/** 本次对话触发生成的新技能名称，未触发时为 null */
private String newSkillName;

/** 本次对话新增的记忆条目数量（提取的新事实 + 偏好 + 决策） */
private int newMemoryCount;

/** 本轮对话中匹配到的技能名称列表，无匹配时为空列表 */
private List<String> skillsMatched;
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 4: 新建 SkillUsageTracker 服务（纯内部服务）

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/SkillUsageTracker.java`

- [ ] **Step 1: 创建完整的 SkillUsageTracker 类**

```java
package com.libin.springai.aideepseek.agent.service;

import com.alibaba.fastjson.JSON;
import com.libin.springai.aideepseek.agent.dto.SkillUsageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 使用追踪服务，将每次 skill 匹配、执行结果和创建事件持久化为 JSONL 文件。
 * <p>
 * 文件按 skill 名和日期分片存储在 .memory/skill-usage/<skillName>/<YYYY-MM-DD>.jsonl。
 * 每条记录为一行 JSON，类型包括 match、outcome、created。
 * 纯内部使用，不暴露任何 REST API。所有写操作使用 synchronized 保证线程安全。
 */
@Slf4j
@Service
public class SkillUsageTracker {

    /** 追踪数据根目录 */
    static final Path USAGE_PATH = Paths.get(".memory/skill-usage");

    private volatile boolean initialized = true;

    public SkillUsageTracker() {
        try {
            Files.createDirectories(USAGE_PATH);
        } catch (IOException e) {
            log.error("Failed to create skill-usage directory", e);
            initialized = false;
        }
    }

    /**
     * 记录一次 skill 匹配事件。
     */
    public synchronized void recordMatch(String skillName, List<String> triggers,
                                         String userMessageDigest, int toolCallCount) {
        if (!initialized || skillName == null) return;
        SkillUsageRecord record = new SkillUsageRecord();
        record.setTimestamp(LocalDateTime.now().toString().substring(0, 19));
        record.setType("match");
        record.setSkillName(skillName);
        record.setTriggers(triggers);
        record.setUserMessageDigest(userMessageDigest);
        record.setToolCallCount(toolCallCount);
        appendRecord(skillName, record);
    }

    /**
     * 记录 agent cycle 的执行结果（成功或失败），为每个匹配的 skill 各写一条。
     */
    public synchronized void recordOutcome(List<String> skillNames, String result,
                                           int toolCallCount, String errorMessage) {
        if (!initialized || skillNames == null || skillNames.isEmpty()) return;
        for (String skillName : skillNames) {
            SkillUsageRecord record = new SkillUsageRecord();
            record.setTimestamp(LocalDateTime.now().toString().substring(0, 19));
            record.setType("outcome");
            record.setSkillName(skillName);
            record.setResult(result);
            record.setToolCallCount(toolCallCount);
            if (errorMessage != null && !errorMessage.isBlank()) {
                record.setErrorMessage(errorMessage.length() > 200
                        ? errorMessage.substring(0, 200) : errorMessage);
            }
            appendRecord(skillName, record);
        }
    }

    /**
     * 记录新 skill 创建事件。
     */
    public synchronized void recordCreation(String skillName, String description,
                                            List<String> triggers) {
        if (!initialized || skillName == null) return;
        SkillUsageRecord record = new SkillUsageRecord();
        record.setTimestamp(LocalDateTime.now().toString().substring(0, 19));
        record.setType("created");
        record.setSkillName(skillName);
        record.setDescription(description);
        record.setTriggers(triggers);
        appendRecord(skillName, record);
    }

    // ===== 内部查询方法（供 SkillEvolutionService 使用，不暴露 API） =====

    /**
     * 内部查询指定 skill 在日期范围内的使用记录。
     */
    synchronized List<SkillUsageRecord> queryUsage(String skillName,
                                                    LocalDate fromDate,
                                                    LocalDate toDate) {
        if (!initialized || skillName == null) return Collections.emptyList();
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        List<SkillUsageRecord> records = new ArrayList<>();
        Path skillDir = USAGE_PATH.resolve(skillName);
        if (!Files.exists(skillDir)) return records;

        try (Stream<Path> files = Files.list(skillDir)) {
            List<Path> jsonlFiles = files
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted()
                    .collect(Collectors.toList());

            for (Path file : jsonlFiles) {
                String fileName = file.getFileName().toString();
                String dateStr = fileName.replace(".jsonl", "");
                try {
                    LocalDate fileDate = LocalDate.parse(dateStr);
                    if (fileDate.isBefore(fromDate) || fileDate.isAfter(toDate)) continue;
                } catch (Exception e) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        SkillUsageRecord record = JSON.parseObject(line, SkillUsageRecord.class);
                        records.add(record);
                    } catch (Exception e) {
                        log.warn("Failed to parse JSONL line in {}: {}", file, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to query usage for skill: {}", skillName, e);
        }
        return records;
    }

    /**
     * 统计某 skill 的成功匹配次数（outcome=success 的记录数）。
     */
    synchronized int countSuccessfulMatches(String skillName) {
        List<SkillUsageRecord> all = queryUsage(skillName, null, null);
        return (int) all.stream()
                .filter(r -> "outcome".equals(r.getType()) && "success".equals(r.getResult()))
                .count();
    }

    /**
     * 获取某 skill 的最近 N 条 match 记录（用于富化上下文收集）。
     */
    synchronized List<SkillUsageRecord> getRecentMatches(String skillName, int limit) {
        List<SkillUsageRecord> all = queryUsage(skillName, null, null);
        return all.stream()
                .filter(r -> "match".equals(r.getType()))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取 skill 的总匹配次数（match 记录数）。
     */
    synchronized int countMatches(String skillName) {
        List<SkillUsageRecord> all = queryUsage(skillName, null, null);
        return (int) all.stream().filter(r -> "match".equals(r.getType())).count();
    }

    // ===== 内部方法 =====

    private void appendRecord(String skillName, SkillUsageRecord record) {
        try {
            Path skillDir = USAGE_PATH.resolve(skillName);
            Files.createDirectories(skillDir);
            String today = LocalDate.now().toString();
            Path file = skillDir.resolve(today + ".jsonl");
            String jsonLine = JSON.toJSONString(record) + "\n";
            Files.writeString(file, jsonLine, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.debug("Skill usage recorded: {} type={}", skillName, record.getType());
        } catch (IOException e) {
            log.error("Failed to record skill usage for {}: {}", skillName, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 5: 新建 SkillEvolutionService 服务（核心进化引擎）

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/SkillEvolutionService.java`

**注意：此服务不包含任何 pending/approve/reject 逻辑（全自动），也不暴露评分 API。**

- [ ] **Step 1: 创建完整的 SkillEvolutionService 类**

```java
package com.libin.springai.aideepseek.agent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import com.libin.springai.aideepseek.agent.dto.SkillUsageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 自我进化核心服务，全自动运行，用户无感。
 * <p>
 * 三大核心能力：
 * <ul>
 *   <li>自动评分：usageCount×0.3 + successRate×0.4 + recency×0.3 = 0-100</li>
 *   <li>自动富化：满足阈值 → LLM 分析 → 质量门禁 → 自动应用</li>
 *   <li>自动回滚：7 天内 effectiveness 下降 ≥ 15 分 → 自动恢复上一版本</li>
 * </ul>
 * 所有操作由 AgentService 在每次 execute cycle 后自动触发，不暴露任何 REST API。
 * 进化事件通过 log.info 记录到应用日志。
 */
@Slf4j
@Service
public class SkillEvolutionService {

    @Autowired
    private SkillManager skillManager;

    @Autowired
    private SkillUsageTracker usageTracker;

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    /** 触发自动富化所需的最小匹配次数（成功匹配 = outcome=success） */
    private static final int ENRICH_MATCH_THRESHOLD = 5;

    /** 触发自动富化所需的最低成功率（百分比） */
    private static final int ENRICH_SUCCESS_RATE_THRESHOLD = 80;

    /** 回归检测的分数下降阈值 */
    private static final int REGRESSION_THRESHOLD = 15;

    /** 回归检测窗口（天） */
    private static final int REGRESSION_WINDOW_DAYS = 7;

    /** 富化分析的 LLM 提示词模板 */
    private static final String ENRICH_PROMPT =
            "以下是当前技能文档和最近几次成功使用的上下文。请分析技能文档可以在哪些方面改进：\n" +
            "1. 补充缺失的边界情况和注意事项\n" +
            "2. 澄清模糊或不够具体的步骤\n" +
            "3. 增加更具体的代码示例或模板\n\n" +
            "输出纯 JSON（不要包含任何其他文字）：\n" +
            "{\n" +
            "  \"suggestedContent\": \"改进后的完整技能正文（Markdown格式，不含frontmatter）\",\n" +
            "  \"changesSummary\": \"一句话描述本次改动\"\n" +
            "}\n\n" +
            "当前技能文档：\n";

    /**
     * 记录每次变更前的 effectiveness，key=skillName, value=变更前的 effectiveness。
     * 用于回归检测——对比当前分值与变更前分值。
     */
    private final Map<String, Integer> preChangeScores = new ConcurrentHashMap<>();

    /**
     * 记录每次变更的时间，key=skillName, value=变更时间。
     */
    private final Map<String, LocalDateTime> lastChangeTime = new ConcurrentHashMap<>();

    /**
     * 重新计算 skill 的 effectiveness 评分并更新 frontmatter。
     * <p>
     * 评分公式：effectiveness = usageCount×0.3 + successRate×0.4 + recency×0.3
     *
     * @param skillName 技能名称
     * @return 新的 effectiveness 分数 (0-100)
     */
    public int recalculateScore(String skillName) {
        String rawContent = skillManager.getByName(skillName);
        if (rawContent == null) return 0;
        SkillInfo info = skillManager.parseFrontMatter(rawContent);
        if (info == null) return 0;

        int usageCount = usageTracker.countMatches(skillName);
        int successCount = usageTracker.countSuccessfulMatches(skillName);
        int successRate = usageCount > 0
                ? (int) ((double) successCount / usageCount * 100)
                : 0;
        int recency = computeRecency(info.getLastUsed());

        int usageFactor = (int) (Math.min((double) usageCount / 50, 1.0) * 100);

        int effectiveness = (int) (usageFactor * 0.3 + successRate * 0.4 + recency * 0.3);

        String lastUsed = LocalDateTime.now().toString().substring(0, 19);
        skillManager.updateEffectiveness(skillName, effectiveness, usageCount, successRate, lastUsed);

        log.info("Skill '{}' score recalculated: effectiveness={}, usageCount={}, successRate={}%, recency={}",
                skillName, effectiveness, usageCount, successRate, recency);
        return effectiveness;
    }

    /**
     * 检查是否满足自动富化触发条件。
     *
     * @param skillName 技能名称
     * @return true 如果满足：成功匹配 ≥ 5 次 AND 成功率 ≥ 80%
     */
    public boolean checkEnrichmentThreshold(String skillName) {
        int totalMatches = usageTracker.countMatches(skillName);
        int successMatches = usageTracker.countSuccessfulMatches(skillName);
        if (successMatches < ENRICH_MATCH_THRESHOLD) return false;

        int successRate = totalMatches > 0
                ? (int) ((double) successMatches / totalMatches * 100) : 0;
        return successRate >= ENRICH_SUCCESS_RATE_THRESHOLD;
    }

    /**
     * 自动触发 LLM 富化分析，经质量门禁后自动应用。
     * <p>
     * 流程：LLM 分析 → 质量门禁 → 自动 apply（全程无人工干预）
     *
     * @param skillName 技能名称
     */
    public void generateAndApplyEnrichment(String skillName) {
        String currentContent = skillManager.getByName(skillName);
        if (currentContent == null) return;

        // 1. 收集最近 5 次成功使用的上下文
        List<SkillUsageRecord> recentMatches = usageTracker.getRecentMatches(skillName, 5);
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < recentMatches.size(); i++) {
            SkillUsageRecord r = recentMatches.get(i);
            contextBuilder.append(String.format("%d. [%s] %s (toolCalls=%d)\n",
                    i + 1, r.getTimestamp(), r.getUserMessageDigest(), r.getToolCallCount()));
        }

        // 2. 调用 LLM 生成富化建议
        String suggestedContent;
        String changesSummary;
        try {
            String prompt = ENRICH_PROMPT + currentContent + "\n\n最近成功使用上下文：\n" + contextBuilder;
            String result = deepSeekChatModel.call(prompt,
                    DeepSeekChatOptions.builder().model("deepseek-chat").temperature(0.0).build())
                    .getResult().getOutput().getText();

            String json = cleanJson(result);
            JSONObject obj = JSON.parseObject(json);
            suggestedContent = obj.getString("suggestedContent");
            changesSummary = obj.getString("changesSummary");

            if (suggestedContent == null || suggestedContent.isBlank()) {
                log.warn("Enrichment LLM returned empty content for skill '{}', skipping", skillName);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to generate enrichment for skill '{}': {}", skillName, e.getMessage());
            return;
        }

        // 3. 质量门禁
        if (!qualityGate(skillName, currentContent, suggestedContent)) {
            log.warn("Enrichment failed quality gate for skill '{}', skipping auto-apply", skillName);
            return;
        }

        // 4. 自动应用
        SkillInfo currentInfo = skillManager.parseFrontMatter(currentContent);
        int preScore = currentInfo != null ? currentInfo.getEffectiveness() : 0;
        preChangeScores.put(skillName, preScore);
        lastChangeTime.put(skillName, LocalDateTime.now());

        skillManager.updateSkill(skillName, suggestedContent, "enrichment",
                changesSummary != null ? changesSummary : "LLM auto-enrichment");

        log.info("SKILL AUTO-ENRICHED: '{}' (pre-score={}) — {}", skillName, preScore, changesSummary);
    }

    /**
     * 内部质量门禁——验证富化结果质量。
     * <p>
     * 检查项：
     * <ol>
     *   <li>内容非空</li>
     *   <li>包含核心触发词的至少 50%（防止触发词丢失）</li>
     *   <li>正文长度变化 ≤ ±80%（防止 LLM 输出异常）</li>
     * </ol>
     *
     * @return true 通过质量门禁
     */
    boolean qualityGate(String skillName, String currentContent, String suggestedContent) {
        // 1. 非空检查
        if (suggestedContent == null || suggestedContent.isBlank()) {
            log.warn("Quality gate [content-empty] failed for skill '{}'", skillName);
            return false;
        }

        SkillInfo currentInfo = skillManager.parseFrontMatter(currentContent);

        // 2. 核心触发词保留率 ≥ 50%
        if (currentInfo != null && currentInfo.getTriggers() != null) {
            List<String> originalTriggers = currentInfo.getTriggers();
            String lowerSuggested = suggestedContent.toLowerCase();
            long retainedCount = originalTriggers.stream()
                    .filter(t -> lowerSuggested.contains(t.toLowerCase()))
                    .count();
            double retentionRate = (double) retainedCount / originalTriggers.size();
            if (retentionRate < 0.5) {
                log.warn("Quality gate [triggers-retention={}] failed for skill '{}': {}/{} triggers retained",
                        String.format("%.0f%%", retentionRate * 100), skillName, retainedCount, originalTriggers.size());
                return false;
            }
        }

        // 3. 正文长度变化 ≤ ±80%
        String currentBody = skillManager.extractBody(currentContent);
        double lengthRatio = (double) suggestedContent.length() / Math.max(currentBody.length(), 1);
        if (lengthRatio < 0.2 || lengthRatio > 1.8) {
            log.warn("Quality gate [length-ratio={}] failed for skill '{}': {} → {} chars",
                    String.format("%.1f", lengthRatio), skillName, currentBody.length(), suggestedContent.length());
            return false;
        }

        log.info("Quality gate PASSED for skill '{}': triggers retention OK, length ratio={}",
                skillName, String.format("%.1f", (double) suggestedContent.length() / Math.max(
                        skillManager.extractBody(currentContent).length(), 1)));
        return true;
    }

    /**
     * 检测评分退化并自动回滚。
     * <p>
     * 如果 7 天内 effectiveness 相比变更前下降 ≥ 15 分，自动回滚到上一版本。
     *
     * @param skillName 技能名称
     * @param currentScore 当前 effectiveness
     */
    public void detectAndRevertRegression(String skillName, int currentScore) {
        Integer preScore = preChangeScores.get(skillName);
        LocalDateTime changeTime = lastChangeTime.get(skillName);

        if (preScore == null || changeTime == null) return;

        // 检查是否在 7 天窗口内
        long daysSinceChange = ChronoUnit.DAYS.between(changeTime, LocalDateTime.now());
        if (daysSinceChange > REGRESSION_WINDOW_DAYS) {
            // 超过窗口期，清除记录
            preChangeScores.remove(skillName);
            lastChangeTime.remove(skillName);
            return;
        }

        int drop = preScore - currentScore;
        if (drop >= REGRESSION_THRESHOLD) {
            log.warn("REGRESSION DETECTED for skill '{}': {} → {} (drop={}, {} days ago). Auto-reverting...",
                    skillName, preScore, currentScore, drop, daysSinceChange);

            // 自动回滚：恢复到上一个版本（当前版本 - 1）
            String rawContent = skillManager.getByName(skillName);
            if (rawContent != null) {
                SkillInfo info = skillManager.parseFrontMatter(rawContent);
                if (info != null && info.getVersion() > 1) {
                    skillManager.revertSkill(skillName, info.getVersion() - 1);
                    log.info("SKILL AUTO-REVERTED: '{}' to v{} (effectiveness dropped {} → {})",
                            skillName, info.getVersion() - 1, preScore, currentScore);
                }
            }

            // 清除退化记录，避免重复回滚
            preChangeScores.remove(skillName);
            lastChangeTime.remove(skillName);
        }
    }

    // ===== 内部方法 =====

    /**
     * 计算时效性分数：7天内=100, 30天内=50, 其他=10, 从未用过=0
     */
    private int computeRecency(String lastUsed) {
        if (lastUsed == null || lastUsed.isBlank() || "null".equals(lastUsed)) return 0;
        try {
            LocalDateTime last = LocalDateTime.parse(lastUsed.substring(0, 19));
            long days = ChronoUnit.DAYS.between(last, LocalDateTime.now());
            if (days <= 7) return 100;
            if (days <= 30) return 50;
            return 10;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 清理 LLM 返回的 JSON 字符串（去除 markdown 代码块包裹）。
     */
    private String cleanJson(String raw) {
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.substring(json.indexOf("\n") + 1);
            if (json.endsWith("```")) {
                json = json.substring(0, json.lastIndexOf("```")).trim();
            }
        }
        return json;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS — 注意 `SkillManager.extractBody()` 和 `SkillManager.updateSkill()` 等新方法尚未添加，可预期编译错误（将在 Task 6 中解决）

---

### Task 6: 扩展 SkillManager — 版本管理 + 去重 + frontmatter

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/SkillManager.java`

- [ ] **Step 1: 修改 createSkill() — 添加 evolution 字段到 frontmatter**

找到 `createSkill()` 方法中的 frontmatter 构建部分（约 line 88-96），替换为：

```java
String skillMd = "---\n" +
        "name: " + name + "\n" +
        "description: " + description + "\n" +
        "triggers: " + triggersStr + "\n" +
        "version: 1\n" +
        "score: 0\n" +
        "effectiveness: 0\n" +
        "usageCount: 0\n" +
        "successRate: 0\n" +
        "lastUsed: null\n" +
        "created: " + timestamp + "\n" +
        "---\n\n" +
        content;
```

- [ ] **Step 2: 新增 updateEffectiveness 方法**

在 `incrementScore()` 方法后添加：

```java
/**
 * 更新技能的进化相关 frontmatter 字段（不改版本号）。
 * 这些字段随使用自动变化，不触发版本备份。
 */
public synchronized void updateEffectiveness(String name, int effectiveness,
                                             int usageCount, int successRate, String lastUsed) {
    if (!initialized) return;
    Path skillFile = resolveSkillPath(name);
    if (skillFile == null || !Files.exists(skillFile)) return;
    try {
        String content = Files.readString(skillFile);
        content = upsertFrontMatterField(content, "effectiveness", String.valueOf(effectiveness));
        content = upsertFrontMatterField(content, "usageCount", String.valueOf(usageCount));
        content = upsertFrontMatterField(content, "successRate", String.valueOf(successRate));
        content = upsertFrontMatterField(content, "lastUsed",
                lastUsed != null ? lastUsed : "null");
        Files.writeString(skillFile, content);
    } catch (IOException e) {
        log.error("Failed to update effectiveness for: {}", name, e);
    }
}

/**
 * 在 frontmatter 中 upsert 一个字段：已存在则替换值，不存在则在第二个 --- 前追加。
 */
private String upsertFrontMatterField(String content, String key, String value) {
    String pattern = key + ": .*";
    if (content.matches("(?s)[\\s\\S]*" + pattern + "[\\s\\S]*")) {
        return content.replaceAll(pattern, key + ": " + value);
    } else {
        int endFm = content.indexOf("---", 3);
        if (endFm == -1) return content;
        return content.substring(0, endFm) + key + ": " + value + "\n" + content.substring(endFm);
    }
}
```

- [ ] **Step 3: 新增 updateSkill 方法（带版本备份）**

在 `updateEffectiveness()` 后添加：

```java
/** 每个 skill 最多保留的历史版本数 */
private static final int MAX_VERSIONS = 10;

/**
 * 更新技能内容，创建版本备份后覆盖主文件。
 *
 * @param name          技能名称
 * @param newContent    新的技能正文（不含 frontmatter）
 * @param changeType    变更类型：enrichment / revert / manual
 * @param changeSummary 变更原因的一句话描述
 */
public synchronized void updateSkill(String name, String newContent,
                                     String changeType, String changeSummary) {
    if (!initialized) return;
    Path skillFile = resolveSkillPath(name);
    if (skillFile == null || !Files.exists(skillFile)) {
        log.warn("Skill not found for update: {}", name);
        return;
    }
    try {
        String currentContent = Files.readString(skillFile);
        SkillInfo currentInfo = parseFrontMatter(currentContent);
        if (currentInfo == null) return;

        int currentVersion = currentInfo.getVersion();

        // 1. 备份当前版本
        Path versionsDir = SKILLS_PATH.resolve(name).resolve("versions");
        Files.createDirectories(versionsDir);
        Path versionFile = versionsDir.resolve("v" + currentVersion + ".md");
        String backupContent = "---\n" +
                "version: " + currentVersion + "\n" +
                "changeType: " + changeType + "\n" +
                "changeSummary: " + changeSummary + "\n" +
                "updatedAt: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n" +
                "---\n\n" + currentContent;
        Files.writeString(versionFile, backupContent);

        // 2. 清理超出限制的旧版本
        try (var stream = Files.list(versionsDir)) {
            List<Path> versionFiles = stream
                    .filter(p -> p.getFileName().toString().startsWith("v"))
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .collect(Collectors.toList());
            while (versionFiles.size() >= MAX_VERSIONS) {
                Path oldest = versionFiles.remove(0);
                Files.deleteIfExists(oldest);
            }
        }

        // 3. 重写主文件（新版本，保留所有 frontmatter 字段并更新）
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String triggersStr = currentInfo.getTriggers() != null
                ? currentInfo.getTriggers().stream()
                    .map(t -> "\"" + t + "\"")
                    .collect(Collectors.joining(", ", "[", "]"))
                : "[]";

        String updatedMd = "---\n" +
                "name: " + name + "\n" +
                "description: " + (currentInfo.getDescription() != null ? currentInfo.getDescription() : "") + "\n" +
                "triggers: " + triggersStr + "\n" +
                "version: " + (currentVersion + 1) + "\n" +
                "score: " + currentInfo.getScore() + "\n" +
                "effectiveness: " + currentInfo.getEffectiveness() + "\n" +
                "usageCount: " + currentInfo.getUsageCount() + "\n" +
                "successRate: " + currentInfo.getSuccessRate() + "\n" +
                "lastUsed: " + (currentInfo.getLastUsed() != null ? currentInfo.getLastUsed() : "null") + "\n" +
                "created: " + (currentInfo.getCreated() != null ? currentInfo.getCreated() : timestamp) + "\n" +
                "updatedAt: " + timestamp + "\n" +
                "---\n\n" +
                newContent;

        Files.writeString(skillFile, updatedMd);
        log.info("Skill updated: {} v{} -> v{} ({})", name, currentVersion, currentVersion + 1, changeType);
    } catch (IOException e) {
        log.error("Failed to update skill: {}", name, e);
    }
}
```

- [ ] **Step 4: 新增 revertSkill 方法（内部方法，供进化服务自动回滚用）**

```java
/**
 * 回滚 skill 到指定版本（内部方法，由 SkillEvolutionService 自动调用）。
 */
public synchronized void revertSkill(String name, int targetVersion) {
    String versionContent = getVersionContent(name, targetVersion);
    if (versionContent == null) {
        log.warn("Version {} not found for skill: {}", targetVersion, name);
        return;
    }
    // 从备份文件中提取原始正文（跳过两层 frontmatter：备份元数据 + 原始 frontmatter）
    String body = versionContent;
    if (versionContent.startsWith("---")) {
        int end = versionContent.indexOf("---", 3);
        if (end != -1) {
            String afterFirstFm = versionContent.substring(end + 3).trim();
            if (afterFirstFm.startsWith("---")) {
                int end2 = afterFirstFm.indexOf("---", 3);
                if (end2 != -1) {
                    body = afterFirstFm.substring(end2 + 3).trim();
                } else {
                    body = afterFirstFm;
                }
            } else {
                body = afterFirstFm;
            }
        }
    }
    updateSkill(name, body, "revert",
            "Auto-reverted to v" + targetVersion + " due to effectiveness regression");
}
```

- [ ] **Step 5: 新增内部版本查询方法**

```java
/**
 * 获取指定版本的完整内容（内部方法）。
 */
synchronized String getVersionContent(String name, int version) {
    Path versionFile = SKILLS_PATH.resolve(name).resolve("versions").resolve("v" + version + ".md");
    if (!Files.exists(versionFile)) return null;
    try {
        return Files.readString(versionFile);
    } catch (IOException e) {
        log.error("Failed to read version {} for skill: {}", version, name);
        return null;
    }
}
```

- [ ] **Step 6: 扩展 parseFrontMatter 支持新字段**

在 `parseFrontMatter()` 方法的 switch 块中添加以下 case：

```java
case "effectiveness":
    info.setEffectiveness(Integer.parseInt(value));
    break;
case "usageCount":
    info.setUsageCount(Integer.parseInt(value));
    break;
case "successRate":
    info.setSuccessRate(Integer.parseInt(value));
    break;
case "lastUsed":
    info.setLastUsed("null".equals(value) ? null : value);
    break;
```

- [ ] **Step 7: extractBody 方法可见性改为 package-private**

找到 `private String extractBody(String content)` 方法，将 `private` 改为**无修饰符**（package-private），使 `SkillEvolutionService.qualityGate()` 可调用：

```java
String extractBody(String content) {
    int end = content.indexOf("---", 3);
    if (end == -1) return content;
    return content.substring(end + 3).trim();
}
```

- [ ] **Step 8: 去重检查方法**

在类末尾添加：

```java
/**
 * 语义去重检查——通过 LLM 判断候选 skill 是否与已有 skill 高度重复。
 * 如果 LLM 调用失败，默认返回 false（fail-open：不阻止创建）。
 *
 * @param candidateDescription 候选 skill 的描述
 * @param candidateTriggers    候选 skill 的触发词
 * @param existingSkillsJson   已有 skill 的 JSON 表示
 * @param llmCaller            执行 LLM 调用的函数接口
 * @return true 如果 LLM 判定为重复（应跳过创建）
 */
public boolean checkDuplicate(String candidateDescription,
                              List<String> candidateTriggers,
                              String existingSkillsJson,
                              java.util.function.Function<String, String> llmCaller) {
    if (existingSkillsJson == null || existingSkillsJson.equals("[]")) return false;

    String prompt = "候选新技能：\n" +
            "描述：" + candidateDescription + "\n" +
            "触发词：" + String.join(", ", candidateTriggers) + "\n\n" +
            "已有技能列表：\n" + existingSkillsJson + "\n\n" +
            "请判断候选新技能是否与已有技能中的某一个高度重复（覆盖相同的任务场景）。" +
            "仅回复 YES 或 NO。";

    try {
        String response = llmCaller.apply(prompt);
        return response != null && response.trim().toUpperCase().contains("YES");
    } catch (Exception e) {
        log.warn("Dedup check failed (fail-open): {}", e.getMessage());
        return false;
    }
}
```

- [ ] **Step 9: 编译验证**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 7: 集成 AgentService — 注入追踪和全自动进化调用

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/AgentService.java`

- [ ] **Step 1: 注入新服务**

在现有 `@Autowired` 字段区添加：

```java
@Autowired
private SkillUsageTracker skillUsageTracker;

@Autowired
private SkillEvolutionService skillEvolutionService;
```

同时添加 import：

```java
import java.util.ArrayList;
```

- [ ] **Step 2: 在 executeCycleWithEvents() 的 skill match 后注入追踪**

找到 `Map<String, String> skills = skillManager.match(userMessage);` 这一行。在其后、`response.setMemoriesUsed(...)` 之前添加：

```java
// === 进化追踪：记录 skill 匹配 + 自动评分 ===
List<String> matchedSkillNames = new ArrayList<>(skills.keySet());
response.setSkillsMatched(matchedSkillNames);

if (!matchedSkillNames.isEmpty()) {
    String digest = userMessage.length() > 200
            ? userMessage.substring(0, 200) : userMessage;
    for (String skillName : matchedSkillNames) {
        skillUsageTracker.recordMatch(skillName,
                getTriggersForSkill(skillName), digest, 0);
        // 每次匹配自动重算评分
        skillEvolutionService.recalculateScore(skillName);
    }
}
// === 追踪结束 ===
```

- [ ] **Step 3: 替换 incrementScore 调用**

找到现有的 `for (String skillName : skills.keySet()) { skillManager.incrementScore(skillName); }`（约 line 172-174），**删除这个循环**。因为上面 Step 2 已经调用了 `recalculateScore()`，不再需要简单的 `incrementScore()`。

- [ ] **Step 4: 在 agent cycle 成功后记录 outcome 并触发进化**

找到 `response.setReply(reply);` 和 `int toolCallCount = agentTools.getAndResetToolCallCount();`（约 line 214-216）。在 `response.setToolCallCount(toolCallCount);` 之后添加：

```java
// === 进化追踪：记录执行结果 + 自动富化 + 退化检测 ===
if (!matchedSkillNames.isEmpty()) {
    skillUsageTracker.recordOutcome(matchedSkillNames, "success", toolCallCount, null);
    for (String skillName : matchedSkillNames) {
        int newScore = skillEvolutionService.recalculateScore(skillName);
        // 检查是否触发自动富化
        if (skillEvolutionService.checkEnrichmentThreshold(skillName)) {
            log.info("Enrichment threshold reached for skill: {}", skillName);
            skillEvolutionService.generateAndApplyEnrichment(skillName);
        }
        // 检查是否需要自动回滚（退化检测）
        skillEvolutionService.detectAndRevertRegression(skillName, newScore);
    }
}
// === 进化追踪结束 ===
```

- [ ] **Step 5: 在 LLM 异常时记录 failure**

在 `} catch (Exception e) {` 块内（约 line 199），在 `String errorMsg = ...` 和 `replyBuilder.append(errorMsg);` 之后添加：

```java
// === 进化追踪：记录失败结果 ===
if (!matchedSkillNames.isEmpty()) {
    skillUsageTracker.recordOutcome(matchedSkillNames, "failure", 0, e.getMessage());
}
// === 追踪结束 ===
```

- [ ] **Step 6: 在 skill 生成后调用 recordCreation + 去重检查**

在 `skillManager.createSkill(gen.getName(), gen.getDescription(), gen.getTriggers(), gen.getContent());`（约 line 262）之前添加去重检查，之后添加创建追踪：

```java
// --- 去重检查 ---
String existingSkillsJson = JSON.toJSONString(
        skillManager.listAll().stream()
                .map(s -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name", s.getName());
                    m.put("description", s.getDescription());
                    m.put("triggers", s.getTriggers());
                    return m;
                })
                .collect(Collectors.toList()));
boolean isDuplicate = skillManager.checkDuplicate(
        gen.getDescription(), gen.getTriggers(), existingSkillsJson,
        prompt -> deepSeekChatModel.call(prompt,
                DeepSeekChatOptions.builder().model("deepseek-chat").temperature(0.0).build())
                .getResult().getOutput().getText());
if (isDuplicate) {
    log.info("Duplicate skill skipped: {} (matches existing skill)", gen.getName());
} else {
    skillManager.createSkill(gen.getName(), gen.getDescription(),
            gen.getTriggers(), gen.getContent());
    response.setSkillTriggered(true);
    response.setNewSkillName(gen.getName());
    // 追踪创建事件
    skillUsageTracker.recordCreation(gen.getName(), gen.getDescription(), gen.getTriggers());
}
// --- 去重结束 ---
```

注意：需要删除原来外面那层 `skillManager.createSkill(...)` 调用和后续的 `response.setSkillTriggered(true)`/`response.setNewSkillName(gen.getName())` 行，避免重复。

- [ ] **Step 7: 添加辅助方法**

在类末尾（`SkillGeneration` 内部类之后）添加：

```java
/**
 * 获取指定 skill 的 triggers 列表。
 */
private List<String> getTriggersForSkill(String skillName) {
    SkillInfo info = skillManager.parseFrontMatter(skillManager.getByName(skillName));
    return info != null && info.getTriggers() != null
            ? info.getTriggers() : List.of();
}
```

- [ ] **Step 8: 编译验证**

```bash
./mvnw -pl ai-deepseek compile -q
```

Expected: BUILD SUCCESS

---

### Task 8: 编写 SkillUsageTracker 单元测试

**Files:**
- Create: `ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/SkillUsageTrackerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.libin.springai.aideepseek.agent.service;

import com.libin.springai.aideepseek.agent.dto.SkillUsageRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillUsageTrackerTest {

    private SkillUsageTracker tracker;

    @BeforeEach
    void setUp() throws IOException {
        Path testDir = Paths.get(".memory/skill-usage");
        if (Files.exists(testDir)) {
            try (var stream = Files.walk(testDir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
        tracker = new SkillUsageTracker();
    }

    @Test
    void testRecordMatch() {
        tracker.recordMatch("test-skill", List.of("java", "spring"),
                "帮我写一个 Spring Controller", 3);
        List<SkillUsageRecord> records = tracker.queryUsage("test-skill", null, null);
        assertFalse(records.isEmpty());
        SkillUsageRecord r = records.get(0);
        assertEquals("match", r.getType());
        assertEquals("test-skill", r.getSkillName());
        assertEquals(2, r.getTriggers().size());
        assertTrue(r.getUserMessageDigest().contains("Spring Controller"));
    }

    @Test
    void testRecordOutcomeSuccess() {
        tracker.recordOutcome(List.of("test-skill"), "success", 5, null);
        List<SkillUsageRecord> records = tracker.queryUsage("test-skill", null, null);
        assertFalse(records.isEmpty());
        assertEquals("outcome", records.get(0).getType());
        assertEquals("success", records.get(0).getResult());
    }

    @Test
    void testRecordOutcomeFailure() {
        tracker.recordOutcome(List.of("test-skill"), "failure", 0, "LLM timeout");
        List<SkillUsageRecord> records = tracker.queryUsage("test-skill", null, null);
        assertEquals("failure", records.get(0).getResult());
        assertTrue(records.get(0).getErrorMessage().contains("LLM timeout"));
    }

    @Test
    void testRecordOutcomeMultipleSkills() {
        tracker.recordOutcome(List.of("skill-a", "skill-b"), "success", 3, null);
        assertFalse(tracker.queryUsage("skill-a", null, null).isEmpty());
        assertFalse(tracker.queryUsage("skill-b", null, null).isEmpty());
    }

    @Test
    void testRecordCreation() {
        tracker.recordCreation("new-skill", "A test skill", List.of("test", "demo"));
        List<SkillUsageRecord> records = tracker.queryUsage("new-skill", null, null);
        assertFalse(records.isEmpty());
        assertEquals("created", records.get(0).getType());
        assertEquals("A test skill", records.get(0).getDescription());
        assertEquals(2, records.get(0).getTriggers().size());
    }

    @Test
    void testQueryWithDateRange() {
        tracker.recordMatch("date-skill", List.of("x"), "test message", 1);
        List<SkillUsageRecord> today = tracker.queryUsage("date-skill",
                LocalDate.now(), LocalDate.now());
        assertFalse(today.isEmpty());
        List<SkillUsageRecord> future = tracker.queryUsage("date-skill",
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2));
        assertTrue(future.isEmpty());
    }

    @Test
    void testCountSuccessfulMatches() {
        tracker.recordOutcome(List.of("count-skill"), "success", 3, null);
        tracker.recordOutcome(List.of("count-skill"), "success", 2, null);
        tracker.recordOutcome(List.of("count-skill"), "failure", 1, "error");
        assertEquals(2, tracker.countSuccessfulMatches("count-skill"));
    }

    @Test
    void testCountMatches() {
        tracker.recordMatch("count-skill", List.of("x"), "msg1", 1);
        tracker.recordMatch("count-skill", List.of("y"), "msg2", 2);
        tracker.recordMatch("count-skill", List.of("z"), "msg3", 0);
        assertEquals(3, tracker.countMatches("count-skill"));
    }

    @Test
    void testGetRecentMatches() {
        tracker.recordMatch("recent-skill", List.of("a"), "Message 1", 1);
        tracker.recordMatch("recent-skill", List.of("b"), "Message 2", 2);
        tracker.recordMatch("recent-skill", List.of("c"), "Message 3", 3);
        tracker.recordMatch("recent-skill", List.of("d"), "Message 4", 4);
        tracker.recordMatch("recent-skill", List.of("e"), "Message 5", 5);
        tracker.recordMatch("recent-skill", List.of("f"), "Message 6", 6);

        List<SkillUsageRecord> recent = tracker.getRecentMatches("recent-skill", 3);
        assertEquals(3, recent.size());
        // 最近的在最前面
        assertTrue(recent.get(0).getUserMessageDigest().contains("Message 6"));
    }

    @Test
    void testEmptySkillReturnsEmpty() {
        assertTrue(tracker.queryUsage("nonexistent", null, null).isEmpty());
        assertEquals(0, tracker.countSuccessfulMatches("nonexistent"));
        assertEquals(0, tracker.countMatches("nonexistent"));
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./mvnw -pl ai-deepseek -Dtest=SkillUsageTrackerTest test -DskipTests=false
```

Expected: Tests run: 10, Failures: 0

---

### Task 9: 运行全部现有测试确保向后兼容

**Files:** 无新文件

- [ ] **Step 1: 运行所有 ai-deepseek 测试**

```bash
cd "D:\10.code\10.springAi\ai-demo"
./mvnw -pl ai-deepseek test -DskipTests=false
```

Expected: All existing tests pass — 验证新增代码没有破坏现有的 MemoryStoreTest、SkillManagerTest、AgentServiceTest

- [ ] **Step 2: 如果测试失败，按错误信息定位修复**

常见问题：
- `SkillManagerTest` 可能因为 `createSkill()` 新增的 frontmatter 字段导致断言失败 → 更新测试中的预期 frontmatter 内容
- `AgentServiceTest` 可能因为新增的 `@Autowired` 字段导致 context 加载失败 → 确认 `SkillUsageTracker` 和 `SkillEvolutionService` 都有 `@Service` 注解
- `NullPointerException` 在新字段解析时 → 检查 `parseFrontMatter` 中 `parseInt` 对空字符串的处理，添加 try-catch

---

### Task 10: 集成验证 — 启动服务验证进化闭环

**Files:** 无新文件

- [ ] **Step 1: 启动 ai-deepseek 服务**

```bash
./mvnw -pl ai-deepseek spring-boot:run
```

Expected: 服务在 port 8091 启动，日志无异常

- [ ] **Step 2: 运行 demo 验证 skill 创建包含 evolution 字段**

```bash
curl -s -X POST "http://localhost:8091/agent/demo/run" | python3 -c "
import sys, json
d = json.load(sys.stdin)
for r in d['data']['rounds']:
    resp = r['response']
    print(f'Round {r[\"round\"]}: skillTriggered={resp[\"skillTriggered\"]}, newSkill={resp.get(\"newSkillName\",\"N/A\")}, skillsMatched={resp.get(\"skillsMatched\",[])}')
"
```

- [ ] **Step 3: 检查生成的 skill 文件 frontmatter**

```bash
head -15 .memory/skills/*.md
```

Expected: frontmatter 包含 `effectiveness: 0`、`usageCount: 0`、`successRate: 0`、`lastUsed: null`

- [ ] **Step 4: 模拟多次使用同一 skill，验证自动评分**

```bash
# 查看 demo 生成的所有 skill
ls .memory/skills/

# 针对某个 skill（假设名为 spring-controller-pattern），发送 5 次匹配消息
for i in 1 2 3 4 5; do
  echo "=== Round $i ==="
  curl -s -X POST "http://localhost:8091/agent/chat" \
    --data-urlencode "message=帮我写一个Spring Controller，包含参数校验" -G | \
    python3 -c "import sys,json;d=json.load(sys.stdin);print('skillsMatched:',d['data'].get('skillsMatched',[]))"
  sleep 1
done

# 检查 skill 文件的 effectiveness/usageCount 是否自动更新
head -15 .memory/skills/spring-controller-pattern.md
```

Expected: `usageCount` 递增，`effectiveness` 自动更新，`lastUsed` 不再为 null

- [ ] **Step 5: 检查使用追踪 JSONL 文件**

```bash
# 查看追踪目录结构
find .memory/skill-usage -type f -name "*.jsonl" | head -10

# 查看某个 skill 的追踪内容
cat .memory/skill-usage/spring-controller-pattern/$(date +%Y-%m-%d).jsonl 2>/dev/null | python3 -m json.tool | head -40
```

Expected: 包含 type=match 和 type=outcome 的记录

- [ ] **Step 6: 查看进化日志**

在服务日志中搜索进化事件：

```bash
# 在启动服务的终端中观察以下日志关键字：
# "SKILL AUTO-ENRICHED"
# "SKILL AUTO-REVERTED"
# "Enrichment threshold reached"
# "Quality gate"
# "score recalculated"
```

Expected: 可见自动评分日志；如果匹配成功 ≥ 5 次且成功率达 80% 则可见自动富化日志

---

### Task 11: 验证自动富化 + 自动回滚闭环（边界条件测试）

**Files:** 无新文件

- [ ] **Step 1: 检查富化阈值日志**

多次调用同一 skill（确保 LLM 调用无异常），观察日志中何时出现：
```
Enrichment threshold reached for skill: <skillName>
SKILL AUTO-ENRICHED: '<skillName>' (pre-score=XX) — <changesSummary>
```

- [ ] **Step 2: 验证版本备份已创建**

```bash
ls .memory/skills/<skillName>/versions/
```

Expected: 富化触发后应有 `v1.md` 等版本备份文件

- [ ] **Step 3: 验证质量门禁**

如果 LLM 返回的富化内容有问题（如丢失触发词），日志中应出现：
```
Quality gate [triggers-retention=XX%] failed for skill '<name>'
```
且 skill 文件不发生变更。

---

## 实施顺序

```
Task 1 (SkillInfo DTO)
  └─▶ Task 2 (SkillUsageRecord DTO)    [与 Task 1,3 可并行]
  └─▶ Task 3 (AgentResponse DTO)       [与 Task 1,2 可并行]
        └─▶ Task 4 (SkillUsageTracker) [依赖 Task 2]
              └─▶ Task 5 (SkillEvolutionService) [依赖 Task 2,4]
                    └─▶ Task 6 (SkillManager 扩展) [依赖 Task 1]
                          └─▶ Task 7 (AgentService 集成) [依赖 Task 4,5,6]
                                └─▶ Task 8 (单元测试)     [依赖 Task 4]
                                      └─▶ Task 9 (回归测试)
                                            └─▶ Task 10 (集成验证)
                                                  └─▶ Task 11 (闭环验证)
```

## 关键设计约束

1. **所有改动限于 `ai-deepseek/agent/` 包** — 不修改其他模块
2. **不新增任何 REST API 端点** — HermesAgentController 保持不变
3. **全自动，用户无感** — 所有进化逻辑在 AgentService 执行循环中自动触发
4. **自动富化需过质量门禁** — LLM 输出需通过触发词保留率 + 长度变化检查
5. **自动回滚** — effectiveness 7 天内下降 ≥ 15 分自动恢复到上一版本
6. **沿用 `.memory/` 文件系统** — 不引入数据库
7. **与现有 DTO 向后兼容** — 新字段使用 `int` / `String` 默认值（0 / null）
8. **所有进化事件仅通过 log.info 可见** — 用户通过应用日志观察进化过程
