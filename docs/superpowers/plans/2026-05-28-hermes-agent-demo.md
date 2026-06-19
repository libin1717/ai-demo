# Hermes Agent Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 ai-deepseek 模块中构建具有持久记忆（本地 .memory/ 文件）+ 自动技能沉淀的 AI Agent，通过 4 轮预设对话展示 "越用越懂你" 的成长过程。

**Architecture:** Spring Boot Controller → AgentService（核心循环：记忆检索 → 技能匹配 → LLM + Tool Calling → 二次 LLM 抽取 → 沉淀）→ MemoryStore/SkillManager 本地文件存储。所有工具操作限制在 .sandbox/ 隔离沙箱内。

**Tech Stack:** Spring Boot 3.5.5, Spring AI 1.0.1, DeepSeek (deepseek-v4-flash 用于 Function Calling，deepseek-reasoner 可选回退), Java 17, Lombok, FastJSON

---

### Task 1: 目录结构与配置

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/` (directory)
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/` (directory)
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/tool/` (directory)
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/` (directory)
- Create: `ai-deepseek/.memory/.gitkeep`
- Create: `ai-deepseek/.sandbox/.gitkeep`
- Modify: `ai-deepseek/.gitignore`

- [ ] **Step 1: 创建包目录和占位文件**

```bash
mkdir -p ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/{controller,service,tool,dto}
mkdir -p ai-deepseek/.memory/skills
mkdir -p ai-deepseek/.sandbox
touch ai-deepseek/.memory/.gitkeep
touch ai-deepseek/.sandbox/.gitkeep
```

- [ ] **Step 2: 更新 .gitignore**

```bash
cat >> ai-deepseek/.gitignore << 'EOF'
# Hermes Agent demo runtime files
.memory/
.sandbox/
EOF
```

- [ ] **Step 3: 验证目录结构**

```bash
ls -R ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/
ls -la ai-deepseek/.memory/
ls -la ai-deepseek/.sandbox/
```

Expected: 四个子目录 (controller, service, tool, dto)，两个 .gitkeep 文件存在。

- [ ] **Step 4: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/ \
        ai-deepseek/.memory/ ai-deepseek/.sandbox/ ai-deepseek/.gitignore
git commit -m "chore: create agent package structure and sandbox directories"
```

---

### Task 2: AgentResponse DTO

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/AgentResponse.java`

- [ ] **Step 1: 编写 AgentResponse DTO**

```java
package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {

    /** AI 的最终文本回复 */
    private String reply;

    /** 本轮对话中使用的工具调用次数 */
    private int toolCallCount;

    /** 本轮注入的记忆来源（文件名 → 匹配行数） */
    private Map<String, Integer> memoriesUsed;

    /** 本轮是否触发了技能沉淀 */
    private boolean skillTriggered;

    /** 若触发，新生成的技能名称 */
    private String newSkillName;

    /** 本轮新写入的记忆条数 */
    private int newMemoryCount;
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/AgentResponse.java
git commit -m "feat: add AgentResponse DTO"
```

---

### Task 3: MemoryExtraction DTO

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/MemoryExtraction.java`

- [ ] **Step 1: 编写 MemoryExtraction DTO**

```java
package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemoryExtraction {

    /** 从对话中抽取的客观事实 */
    private List<String> facts;

    /** 从对话中抽取的用户偏好 */
    private List<String> preferences;

    /** 从对话中抽取的技术决策 */
    private List<String> decisions;
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/MemoryExtraction.java
git commit -m "feat: add MemoryExtraction DTO"
```

---

### Task 4: SkillInfo DTO

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/SkillInfo.java`

- [ ] **Step 1: 编写 SkillInfo DTO**

```java
package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillInfo {

    private String name;
    private String description;
    private List<String> triggers;
    private int version;
    private int score;
    private String created;
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/dto/SkillInfo.java
git commit -m "feat: add SkillInfo DTO"
```

---

### Task 5: MemoryStore 服务

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/MemoryStore.java`

- [ ] **Step 1: 编写 MemoryStore**

```java
package com.libin.springai.aideepseek.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryStore {

    static final Path MEMORY_PATH = Paths.get(".memory");

    private static final List<String> MEMORY_FILES = List.of("facts.md", "profile.md", "decisions.md");

    public MemoryStore() {
        try {
            Files.createDirectories(MEMORY_PATH.resolve("skills"));
            Path indexPath = MEMORY_PATH.resolve("MEMORY.md");
            if (!Files.exists(indexPath)) {
                Files.writeString(indexPath, "# Memory Index\n\n");
            }
            for (String file : MEMORY_FILES) {
                Path p = MEMORY_PATH.resolve(file);
                if (!Files.exists(p)) {
                    String heading = file.replace(".md", "");
                    heading = heading.substring(0, 1).toUpperCase() + heading.substring(1);
                    Files.writeString(p, "# " + heading + "\n\n");
                }
            }
        } catch (IOException e) {
            log.error("Failed to initialize .memory directory", e);
        }
    }

    public synchronized void saveFact(String fact) {
        appendToFile(MEMORY_PATH.resolve("facts.md"), "- " + fact + "\n");
        appendToIndex("fact", fact);
    }

    public synchronized void savePreference(String preference) {
        appendToFile(MEMORY_PATH.resolve("profile.md"), "- " + preference + "\n");
        appendToIndex("preference", preference);
    }

    public synchronized void saveDecision(String decision) {
        appendToFile(MEMORY_PATH.resolve("decisions.md"), "- " + decision + "\n");
        appendToIndex("decision", decision);
    }

    private void appendToFile(Path filePath, String line) {
        try {
            Files.writeString(filePath, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append to {}", filePath, e);
        }
    }

    private void appendToIndex(String type, String content) {
        String timestamp = LocalDateTime.now().toString().substring(0, 19);
        String truncated = content.length() > 80 ? content.substring(0, 80) + "..." : content;
        appendToFile(MEMORY_PATH.resolve("MEMORY.md"),
                "- [" + timestamp + "] " + type + ": " + truncated + "\n");
    }

    /**
     * 关键词检索记忆。返回匹配的文件名 → 匹配行列表。
     * 完全匹配（整词匹配）排在部分匹配前。
     */
    public Map<String, List<String>> search(String query) {
        Map<String, List<String>> results = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return results;
        }
        String lowerQuery = query.toLowerCase();
        for (String file : MEMORY_FILES) {
            try {
                List<String> lines = Files.readAllLines(MEMORY_PATH.resolve(file));
                List<String> exact = new ArrayList<>();
                List<String> partial = new ArrayList<>();
                for (String line : lines) {
                    if (!line.startsWith("- ")) continue;
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains(lowerQuery)) {
                        // 检查是否包含完整单词匹配
                        if (lowerLine.contains(" " + lowerQuery + " ")
                                || lowerLine.endsWith(" " + lowerQuery)
                                || lowerLine.contains(lowerQuery + ",")) {
                            exact.add(line.trim());
                        } else {
                            partial.add(line.trim());
                        }
                    }
                }
                List<String> combined = new ArrayList<>();
                combined.addAll(exact);
                combined.addAll(partial);
                if (!combined.isEmpty()) {
                    results.put(file, combined);
                }
            } catch (IOException e) {
                log.error("Failed to search in {}", file, e);
            }
        }
        return results;
    }

    public Map<String, Integer> getMemorySummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (String file : MEMORY_FILES) {
            try {
                List<String> lines = Files.readAllLines(MEMORY_PATH.resolve(file));
                long count = lines.stream().filter(l -> l.startsWith("- ")).count();
                summary.put(file, (int) count);
            } catch (IOException e) {
                summary.put(file, 0);
            }
        }
        return summary;
    }

    public String getMemoryFile(String fileName) {
        Path filePath = MEMORY_PATH.resolve(fileName).normalize();
        if (!filePath.startsWith(MEMORY_PATH.normalize())) {
            return null;
        }
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("Failed to read memory file: {}", fileName, e);
            return null;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 编写 MemoryStoreTest 并运行**

```bash
cat > ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/MemoryStoreTest.java << 'JAVAEOF'
package com.libin.springai.aideepseek.agent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryStoreTest {

    private MemoryStore memoryStore;

    @BeforeEach
    void setUp() throws IOException {
        Path memPath = Path.of(".memory");
        if (Files.exists(memPath)) {
            Files.walk(memPath)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        memoryStore = new MemoryStore();
    }

    @Test
    void shouldInitializeEmptyDirectoryStructure() {
        assertTrue(Files.exists(MemoryStore.MEMORY_PATH.resolve("facts.md")));
        assertTrue(Files.exists(MemoryStore.MEMORY_PATH.resolve("profile.md")));
        assertTrue(Files.exists(MemoryStore.MEMORY_PATH.resolve("decisions.md")));
        assertTrue(Files.exists(MemoryStore.MEMORY_PATH.resolve("MEMORY.md")));
        assertTrue(Files.exists(MemoryStore.MEMORY_PATH.resolve("skills")));
    }

    @Test
    void shouldSaveAndSearchFact() {
        memoryStore.saveFact("项目使用 Spring Boot 3.5.5");
        memoryStore.saveFact("端口号是 8091");

        Map<String, List<String>> results = memoryStore.search("Spring Boot");
        assertEquals(1, results.size());
        assertTrue(results.containsKey("facts.md"));
        assertTrue(results.get("facts.md").get(0).contains("Spring Boot"));
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        memoryStore.saveFact("项目使用 Spring Boot 3.5.5");
        Map<String, List<String>> results = memoryStore.search("Docker");
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldSortExactMatchBeforePartial() {
        memoryStore.saveFact("用户喜欢使用 Java");
        memoryStore.saveFact("项目中大量使用了 Java Stream API");

        Map<String, List<String>> results = memoryStore.search("Java");
        List<String> matches = results.get("facts.md");
        assertEquals(2, matches.size());
        assertTrue(matches.get(0).contains("用户喜欢使用 Java"));
    }

    @Test
    void shouldReportMemorySummary() {
        memoryStore.saveFact("事实1");
        memoryStore.savePreference("偏好1");
        memoryStore.savePreference("偏好2");
        memoryStore.saveDecision("决策1");

        Map<String, Integer> summary = memoryStore.getMemorySummary();
        assertEquals(1, summary.get("facts.md"));
        assertEquals(2, summary.get("profile.md"));
        assertEquals(1, summary.get("decisions.md"));
    }
}
JAVAEOF
./mvnw -pl ai-deepseek -Dtest=MemoryStoreTest test -DskipTests=false
```

Expected: Tests PASS (5/5)

- [ ] **Step 4: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/MemoryStore.java \
        ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/MemoryStoreTest.java
git commit -m "feat: implement MemoryStore with keyword search and test"
```

---

### Task 6: SkillManager 服务

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/SkillManager.java`

- [ ] **Step 1: 编写 SkillManager**

```java
package com.libin.springai.aideepseek.agent.service;

import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillManager {

    static final Path SKILLS_PATH = Paths.get(".memory/skills");

    public SkillManager() {
        try {
            Files.createDirectories(SKILLS_PATH);
        } catch (IOException e) {
            log.error("Failed to create skills directory", e);
        }
    }

    /**
     * 创建技能文档（Markdown + YAML front matter）
     */
    public synchronized void createSkill(String name, String description, List<String> triggers, String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String triggersStr = triggers.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", ", "[", "]"));

        String skillMd = "---\n" +
                "name: " + name + "\n" +
                "description: " + description + "\n" +
                "triggers: " + triggersStr + "\n" +
                "version: 1\n" +
                "created: " + timestamp + "\n" +
                "score: 0\n" +
                "---\n\n" +
                content;

        Path skillFile = SKILLS_PATH.resolve(name + ".md");
        try {
            Files.writeString(skillFile, skillMd);
            log.info("Skill created: {}", name);
        } catch (IOException e) {
            log.error("Failed to create skill: {}", name, e);
        }
    }

    /**
     * 根据 query 匹配技能。遍历所有技能文档的 triggers 字段，关键词匹配。
     */
    public Map<String, String> match(String query) {
        Map<String, String> matched = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return matched;
        }
        String lowerQuery = query.toLowerCase();
        try {
            if (!Files.exists(SKILLS_PATH)) return matched;
            List<Path> skillFiles = Files.list(SKILLS_PATH)
                    .filter(p -> p.toString().endsWith(".md"))
                    .collect(Collectors.toList());

            for (Path skillFile : skillFiles) {
                String content = Files.readString(skillFile);
                SkillInfo info = parseFrontMatter(content);
                if (info != null && info.getTriggers() != null) {
                    for (String trigger : info.getTriggers()) {
                        if (lowerQuery.contains(trigger.toLowerCase())) {
                            String body = extractBody(content);
                            matched.put(info.getName(), body);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to match skills for query: {}", query, e);
        }
        return matched;
    }

    public synchronized void incrementScore(String skillName) {
        Path skillFile = SKILLS_PATH.resolve(skillName + ".md");
        if (!Files.exists(skillFile)) return;
        try {
            String content = Files.readString(skillFile);
            String updated = content.replaceFirst(
                    "score: (\\d+)",
                    m -> "score: " + (Integer.parseInt(m.group(1)) + 1));
            Files.writeString(skillFile, updated);
        } catch (IOException e) {
            log.error("Failed to increment score for: {}", skillName, e);
        }
    }

    public List<SkillInfo> listAll() {
        List<SkillInfo> skills = new ArrayList<>();
        try {
            if (!Files.exists(SKILLS_PATH)) return skills;
            List<Path> skillFiles = Files.list(SKILLS_PATH)
                    .filter(p -> p.toString().endsWith(".md"))
                    .collect(Collectors.toList());
            for (Path skillFile : skillFiles) {
                String content = Files.readString(skillFile);
                SkillInfo info = parseFrontMatter(content);
                if (info != null) skills.add(info);
            }
        } catch (IOException e) {
            log.error("Failed to list skills", e);
        }
        return skills;
    }

    public String getByName(String skillName) {
        Path skillFile = SKILLS_PATH.resolve(skillName + ".md");
        if (!Files.exists(skillFile)) return null;
        try {
            return Files.readString(skillFile);
        } catch (IOException e) {
            log.error("Failed to read skill: {}", skillName, e);
            return null;
        }
    }

    /**
     * 从 Markdown 中解析 YAML front matter
     */
    SkillInfo parseFrontMatter(String content) {
        if (!content.startsWith("---")) return null;
        int end = content.indexOf("---", 3);
        if (end == -1) return null;
        String fm = content.substring(3, end);

        SkillInfo info = new SkillInfo();
        for (String line : fm.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(":");
            if (colon == -1) continue;
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();

            switch (key) {
                case "name": info.setName(value); break;
                case "description": info.setDescription(value); break;
                case "version": info.setVersion(Integer.parseInt(value)); break;
                case "score": info.setScore(Integer.parseInt(value)); break;
                case "created": info.setCreated(value); break;
                case "triggers":
                    String arr = value.replace("[", "").replace("]", "");
                    info.setTriggers(Arrays.stream(arr.split(","))
                            .map(s -> s.trim().replace("\"", ""))
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList()));
                    break;
            }
        }
        return info;
    }

    /**
     * 提取 front matter 之后的正文内容
     */
    private String extractBody(String content) {
        int end = content.indexOf("---", 3);
        if (end == -1) return content;
        return content.substring(end + 3).trim();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 编写 SkillManagerTest 并运行**

```bash
cat > ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/SkillManagerTest.java << 'JAVAEOF'
package com.libin.springai.aideepseek.agent.service;

import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillManagerTest {

    private SkillManager skillManager;

    @BeforeEach
    void setUp() throws IOException {
        Path skillsPath = Path.of(".memory/skills");
        if (Files.exists(skillsPath)) {
            Files.walk(skillsPath)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        skillManager = new SkillManager();
    }

    @Test
    void shouldCreateAndListSkill() {
        skillManager.createSkill("spring-controller", "Spring Controller pattern",
                List.of("Controller", "REST", "API"),
                "## Pattern\n\nUse @RestController with @RequestMapping.");

        List<SkillInfo> skills = skillManager.listAll();
        assertEquals(1, skills.size());
        assertEquals("spring-controller", skills.get(0).getName());
        assertEquals("Spring Controller pattern", skills.get(0).getDescription());
        assertEquals(3, skills.get(0).getTriggers().size());
    }

    @Test
    void shouldMatchSkillByTrigger() {
        skillManager.createSkill("spring-controller", "Spring Controller pattern",
                List.of("Controller", "REST"), "## Content");
        skillManager.createSkill("unit-test", "JUnit test pattern",
                List.of("test", "JUnit", "Mockito"), "## Content");

        Map<String, String> matched = skillManager.match("帮我写一个 Controller");
        assertEquals(1, matched.size());
        assertTrue(matched.containsKey("spring-controller"));

        matched = skillManager.match("请帮我写测试");
        assertEquals(1, matched.size());
        assertTrue(matched.containsKey("unit-test"));
    }

    @Test
    void shouldReturnEmptyWhenNoMatch() {
        skillManager.createSkill("spring-controller", "...", List.of("Controller"), "...");
        Map<String, String> matched = skillManager.match("今天天气怎么样");
        assertTrue(matched.isEmpty());
    }

    @Test
    void shouldIncrementScore() {
        skillManager.createSkill("test-skill", "desc", List.of("test"), "body");
        skillManager.incrementScore("test-skill");

        List<SkillInfo> skills = skillManager.listAll();
        assertEquals(1, skills.get(0).getScore());
    }

    @Test
    void shouldGetSkillByName() {
        skillManager.createSkill("test-skill", "desc", List.of("test"), "body content");
        String content = skillManager.getByName("test-skill");
        assertNotNull(content);
        assertTrue(content.contains("body content"));
    }

    @Test
    void shouldParseFrontMatter() {
        String md = "---\nname: my-skill\ndescription: My desc\ntriggers: [\"a\", \"b\"]\nversion: 1\nscore: 3\ncreated: 2026-01-01\n---\n\nBody";
        SkillInfo info = skillManager.parseFrontMatter(md);
        assertEquals("my-skill", info.getName());
        assertEquals("My desc", info.getDescription());
        assertEquals(2, info.getTriggers().size());
        assertEquals(1, info.getVersion());
        assertEquals(3, info.getScore());
        assertEquals("2026-01-01", info.getCreated());
    }
}
JAVAEOF
./mvnw -pl ai-deepseek -Dtest=SkillManagerTest test -DskipTests=false
```

Expected: Tests PASS (6/6)

- [ ] **Step 4: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/SkillManager.java \
        ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/SkillManagerTest.java
git commit -m "feat: implement SkillManager with YAML front matter parsing and test"
```

---

### Task 7: AgentTools 工具类

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/tool/AgentTools.java`

- [ ] **Step 1: 编写 AgentTools（带工具调用计数器）**

```java
package com.libin.springai.aideepseek.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class AgentTools {

    private static final Path SANDBOX = Paths.get(".sandbox").toAbsolutePath().normalize();

    private final AtomicInteger toolCallCounter = new AtomicInteger(0);

    public AgentTools() {
        try {
            Files.createDirectories(SANDBOX);
        } catch (IOException e) {
            log.error("Failed to create sandbox directory", e);
        }
    }

    public int getAndResetToolCallCount() {
        return toolCallCounter.getAndSet(0);
    }

    @Tool(description = "读取沙箱中指定文件的完整内容")
    public String readFile(@ToolParam(description = "相对于沙箱根目录的文件路径") String path) {
        toolCallCounter.incrementAndGet();
        Path target = resolveSafe(path);
        if (target == null) return "Error: 路径不在沙箱范围内";
        if (!Files.exists(target)) return "Error: 文件不存在 - " + path;
        try {
            return Files.readString(target);
        } catch (IOException e) {
            return "Error: 读取文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "在沙箱中创建或覆盖写入文件")
    public String writeFile(
            @ToolParam(description = "相对于沙箱根目录的文件路径") String path,
            @ToolParam(description = "要写入的完整文件内容") String content) {
        toolCallCounter.incrementAndGet();
        Path target = resolveSafe(path);
        if (target == null) return "Error: 路径不在沙箱范围内";
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            return "成功写入文件: " + path + " (" + content.length() + " 字符)";
        } catch (IOException e) {
            return "Error: 写入文件失败 - " + e.getMessage();
        }
    }

    @Tool(description = "在沙箱中搜索包含指定关键字的文件，返回匹配的文件路径和行内容")
    public String searchCode(@ToolParam(description = "搜索关键字") String keyword) {
        toolCallCounter.incrementAndGet();
        try (Stream<Path> files = Files.walk(SANDBOX)) {
            List<String> results = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".md")
                            || p.toString().endsWith(".xml") || p.toString().endsWith(".yml"))
                    .flatMap(file -> {
                        try {
                            return Files.readAllLines(file).stream()
                                    .filter(line -> line.contains(keyword))
                                    .map(line -> SANDBOX.relativize(file) + ": " + line.trim());
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .limit(20)
                    .collect(Collectors.toList());
            if (results.isEmpty()) return "未找到包含 '" + keyword + "' 的文件";
            return String.join("\n", results);
        } catch (IOException e) {
            return "Error: 搜索失败 - " + e.getMessage();
        }
    }

    @Tool(description = "列出沙箱中指定目录的文件和子目录结构")
    public String listDirectory(
            @ToolParam(description = "相对于沙箱根目录的路径，留空表示根目录") String path) {
        toolCallCounter.incrementAndGet();
        Path dir = SANDBOX;
        if (path != null && !path.isBlank()) {
            Path resolved = resolveSafe(path);
            if (resolved == null) return "Error: 路径不在沙箱范围内";
            dir = resolved;
        }
        if (!Files.exists(dir)) return "Error: 目录不存在 - " + path;
        if (!Files.isDirectory(dir)) return "Error: 不是目录 - " + path;

        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .map(p -> {
                        String prefix = Files.isDirectory(p) ? "[DIR]  " : "[FILE] ";
                        return prefix + SANDBOX.relativize(p);
                    })
                    .sorted()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "Error: 列出目录失败 - " + e.getMessage();
        }
    }

    /**
     * 路径安全校验：防止路径穿越攻击，限制在 SANDBOX 目录内
     */
    private Path resolveSafe(String path) {
        Path resolved = SANDBOX.resolve(path).normalize();
        if (!resolved.startsWith(SANDBOX)) {
            log.warn("Path traversal attempt blocked: {}", path);
            return null;
        }
        return resolved;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/tool/AgentTools.java
git commit -m "feat: implement AgentTools with sandbox-restricted file operations"
```

---

### Task 8: AgentService 核心循环

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/AgentService.java`

- [ ] **Step 1: 编写 AgentService（含 ChatClient 工具调用 + 工具计数 + 技能沉淀）**

```java
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

    /** 触发技能沉淀的工具调用次数阈值 */
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

    /**
     * 执行单轮 Agent 对话循环
     */
    public AgentResponse executeCycle(String userMessage) {
        AgentResponse response = new AgentResponse();
        response.setSkillTriggered(false);

        // 1. 检索相关记忆和技能
        Map<String, List<String>> memories = memoryStore.search(userMessage);
        Map<String, String> skills = skillManager.match(userMessage);

        response.setMemoriesUsed(memories.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));

        // 若有技能命中，增加评分
        for (String skillName : skills.keySet()) {
            skillManager.incrementScore(skillName);
        }

        // 2. 拼装 System Prompt
        String systemPrompt = buildSystemPrompt(memories, skills);

        // 3. 重置工具计数器，然后用 ChatClient 调用 LLM（带 Tool Calling）
        // DeepSeek 中 deepseek-v4-flash 确认支持 function calling，deepseek-reasoner 可选
        agentTools.getAndResetToolCallCount();

        ChatClient chatClient = chatClientBuilder
                .defaultTools(agentTools)
                .build();

        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.0)
                        .build())
                .call()
                .chatResponse();

        String reply = chatResponse.getResult().getOutput().getText();
        response.setReply(reply);

        // 统计本轮工具调用次数
        int toolCallCount = agentTools.getAndResetToolCallCount();
        response.setToolCallCount(toolCallCount);

        // 4. 二轮 LLM 调用：从对话中抽取记忆
        String conversation = "用户: " + userMessage + "\n\nAI: " + reply;
        String extractResult = deepSeekChatModel.call(EXTRACT_PROMPT + conversation);

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

        // 5. 技能沉淀：工具调用次数 >= 阈值时触发生成
        if (toolCallCount >= SKILL_THRESHOLD) {
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
                // 技能生成失败不阻断主流程
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

    /**
     * 解析 LLM 返回的 JSON 提取结果
     */
    MemoryExtraction parseExtraction(String raw) {
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
     * 解析 LLM 返回的技能生成 JSON
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

    private int countMemoryEntries() {
        return memoryStore.getMemorySummary().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * 技能生成的内部 DTO（public fields 用于 FastJSON 反序列化）
     */
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
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 编写 AgentServiceTest 并运行**

```bash
cat > ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/AgentServiceTest.java << 'JAVAEOF'
package com.libin.springai.aideepseek.agent.service;

import com.libin.springai.aideepseek.agent.dto.MemoryExtraction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceTest {

    private final AgentService agentService = new AgentService();

    @Test
    void shouldParseValidJsonExtraction() {
        String json = "{\"facts\":[\"项目使用 Spring Boot\"],\"preferences\":[\"用户喜欢 @Valid\"],\"decisions\":[]}";
        MemoryExtraction result = agentService.parseExtraction(json);
        assertEquals(1, result.getFacts().size());
        assertEquals("项目使用 Spring Boot", result.getFacts().get(0));
        assertEquals(1, result.getPreferences().size());
        assertEquals("用户喜欢 @Valid", result.getPreferences().get(0));
        assertEquals(0, result.getDecisions().size());
    }

    @Test
    void shouldParseJsonWithMarkdownBlock() {
        String json = "```json\n{\"facts\":[],\"preferences\":[],\"decisions\":[\"选择 JUnit 5\"]}\n```";
        MemoryExtraction result = agentService.parseExtraction(json);
        assertEquals(0, result.getFacts().size());
        assertEquals(0, result.getPreferences().size());
        assertEquals(1, result.getDecisions().size());
        assertEquals("选择 JUnit 5", result.getDecisions().get(0));
    }

    @Test
    void shouldReturnEmptyOnMalformedJson() {
        String badJson = "这不是合法的 JSON";
        MemoryExtraction result = agentService.parseExtraction(badJson);
        assertTrue(result.getFacts().isEmpty());
        assertTrue(result.getPreferences().isEmpty());
        assertTrue(result.getDecisions().isEmpty());
    }

    @Test
    void shouldReturnEmptyOnEmptyString() {
        MemoryExtraction result = agentService.parseExtraction("");
        assertTrue(result.getFacts().isEmpty());
        assertTrue(result.getPreferences().isEmpty());
        assertTrue(result.getDecisions().isEmpty());
    }
}
JAVAEOF
./mvnw -pl ai-deepseek -Dtest=AgentServiceTest test -DskipTests=false
```

Expected: Tests PASS (4/4)

- [ ] **Step 4: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/service/AgentService.java \
        ai-deepseek/src/test/java/com/libin/springai/aideepseek/agent/service/AgentServiceTest.java
git commit -m "feat: implement AgentService core loop with memory extraction"
```

---

### Task 9: HermesAgentController REST API

**Files:**
- Create: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/HermesAgentController.java`

- [ ] **Step 1: 编写 HermesAgentController**

```java
package com.libin.springai.aideepseek.agent.controller;

import com.libin.springai.aicommon.dto.ResultDto;
import com.libin.springai.aideepseek.agent.dto.AgentResponse;
import com.libin.springai.aideepseek.agent.dto.SkillInfo;
import com.libin.springai.aideepseek.agent.service.AgentService;
import com.libin.springai.aideepseek.agent.service.MemoryStore;
import com.libin.springai.aideepseek.agent.service.SkillManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class HermesAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private MemoryStore memoryStore;

    @Autowired
    private SkillManager skillManager;

    @PostMapping("/chat")
    public ResultDto<AgentResponse> chat(@RequestParam String message) {
        AgentResponse response = agentService.executeCycle(message);
        return ResultDto.success(response);
    }

    @GetMapping("/memory")
    public ResultDto<Map<String, Integer>> memorySummary() {
        return ResultDto.success(memoryStore.getMemorySummary());
    }

    @GetMapping("/memory/{fileName}")
    public ResultDto<String> memoryFile(@PathVariable String fileName) {
        String content = memoryStore.getMemoryFile(fileName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }

    @GetMapping("/skills")
    public ResultDto<List<SkillInfo>> skills() {
        return ResultDto.success(skillManager.listAll());
    }

    @GetMapping("/skills/{skillName}")
    public ResultDto<String> skillDetail(@PathVariable String skillName) {
        String content = skillManager.getByName(skillName);
        if (content == null) {
            return ResultDto.fail();
        }
        return ResultDto.success(content);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/HermesAgentController.java
git commit -m "feat: add HermesAgentController REST API endpoints"
```

---

### Task 10: Demo 演示端点

**Files:**
- Modify: `ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/HermesAgentController.java`

- [ ] **Step 1: 在 HermesAgentController 中追加 demo/run 端点**

在 `HermesAgentController.java` 的类末尾（最后一个 `}` 前）追加以下方法：

```java
    @PostMapping("/demo/run")
    public ResultDto<Map<String, Object>> runDemo() {
        String[] messages = {
            "帮我写一个用户注册的 Controller，包含 POST /register 接口，接收 username 和 password 参数，返回注册结果",
            "给刚才的 Controller 加上参数校验，使用 @Valid 注解，用户名不能为空且长度 3-20，密码不能为空且长度 6-50",
            "帮我再写一个订单 Controller，包含创建订单和查询订单两个接口，风格跟之前的保持一致",
            "最后再写一个商品 Controller，包含商品列表查询和商品详情查询接口"
        };

        List<Map<String, Object>> rounds = new java.util.ArrayList<>();
        String beforeState = captureMemoryState();

        for (int i = 0; i < messages.length; i++) {
            AgentResponse response = agentService.executeCycle(messages[i]);
            Map<String, Object> round = new java.util.LinkedHashMap<>();
            round.put("round", i + 1);
            round.put("userMessage", messages[i]);
            round.put("response", response);
            round.put("memoryAfterRound", memoryStore.getMemorySummary());
            round.put("skillsAfterRound", skillManager.listAll());
            rounds.add(round);
        }

        String afterState = captureMemoryState();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("rounds", rounds);
        result.put("memoryBefore", beforeState);
        result.put("memoryAfter", afterState);

        return ResultDto.success(result);
    }

    private String captureMemoryState() {
        StringBuilder sb = new StringBuilder();
        for (String file : List.of("facts.md", "profile.md", "decisions.md")) {
            String content = memoryStore.getMemoryFile(file);
            if (content != null) {
                sb.append("=== ").append(file).append(" ===\n").append(content).append("\n");
            }
        }
        return sb.toString();
    }
```

同时在文件头部的 import 区追加：
```java
import java.util.List;
```
（如果尚未导入）

- [ ] **Step 2: 编译验证**

```bash
./mvnw -pl ai-deepseek compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ai-deepseek/src/main/java/com/libin/springai/aideepseek/agent/controller/HermesAgentController.java
git commit -m "feat: add /agent/demo/run 4-round preset demo endpoint"
```

---

### Task 11: 集成验证

- [ ] **Step 1: 编译整个项目**

```bash
./mvnw -pl ai-deepseek clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行所有 Agent 相关单元测试**

```bash
./mvnw -pl ai-deepseek -Dtest="MemoryStoreTest,SkillManagerTest,AgentServiceTest" test -DskipTests=false
```

Expected: All tests PASS (15 total)

- [ ] **Step 3: 启动应用并测试单轮对话**

```bash
./mvnw -pl ai-deepseek spring-boot:run &
# 等待启动后
curl -s "http://localhost:8091/agent/chat?message=你好"
```

Expected: HTTP 200，返回 JSON 含 reply 字段（AI 回复文本）

- [ ] **Step 4: 检查记忆自动写入**

```bash
curl -s "http://localhost:8091/agent/memory"
```

Expected: 返回 facts/profile/decisions 的条目数（可能为 0，取决于对话内容）

- [ ] **Step 5: 运行完整演示**

```bash
curl -s -X POST "http://localhost:8091/agent/demo/run" | python -m json.tool 2>/dev/null || curl -s -X POST "http://localhost:8091/agent/demo/run"
```

Expected: HTTP 200，返回 4 轮对话的完整结果

- [ ] **Step 6: 验证 .memory/ 目录产出**

```bash
echo "=== .memory/ contents ==="
ls -la .memory/
echo ""
echo "=== MEMORY.md ==="
cat .memory/MEMORY.md
echo ""
echo "=== facts.md ==="
cat .memory/facts.md
echo ""
echo "=== Skills ==="
ls -la .memory/skills/
```

Expected: 包含多行记忆记录，可能有技能文件生成

- [ ] **Step 7: 停止应用并 Commit**

```bash
kill %1 2>/dev/null || pkill -f "ai-deepseek"
git add ai-deepseek/
git commit -m "feat: complete Hermes Agent demo integration verification"
```

---

### Task 12: 最终清理和文档

- [ ] **Step 1: 确保 .memory/ 和 .sandbox/ 被 .gitignore 正确排除**

```bash
git status
```

Expected: .memory/ 和 .sandbox/ 不应出现在 untracked files 中

- [ ] **Step 2: 运行全部测试**

```bash
./mvnw -pl ai-deepseek test -DskipTests=false
```

Expected: All tests PASS

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git diff --cached --stat
git commit -m "chore: finalize Hermes Agent demo with gitignore cleanup"
```
