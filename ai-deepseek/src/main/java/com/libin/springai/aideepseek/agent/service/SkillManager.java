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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 技能管理服务，基于本地文件系统管理 Agent 的可复用技能。
 * <p>
 * 每个技能以 Markdown 文件（.md）存储在 .memory/skills 目录下，
 * 文件头部使用 YAML front matter 记录元数据（名称、描述、触发词、版本、评分、创建时间）。
 * 技能在工具调用次数达到阈值时由 AgentService 自动沉淀生成。
 */
@Slf4j
@Service
public class SkillManager {

    /** 技能文件存储目录路径 */
    static final Path SKILLS_PATH = Paths.get(".memory/skills");

    /** 每个 skill 最多保留的历史版本数 */
    private static final int MAX_VERSIONS = 10;

    /** 服务初始化状态标志，目录创建失败时置为 false */
    private volatile boolean initialized = true;

    /**
     * 构造 SkillManager 实例，确保技能存储目录存在。
     * 创建失败时将 initialized 置为 false，后续操作会被跳过。
     */
    public SkillManager() {
        try {
            Files.createDirectories(SKILLS_PATH);
        } catch (IOException e) {
            log.error("Failed to create skills directory", e);
            initialized = false;
        }
    }

    /**
     * 解析并校验技能文件路径，防止路径遍历攻击。
     *
     * @param skillName 技能名称（不含 .md 后缀）
     * @return 安全的绝对路径，名称无效或路径越出时返回 null
     */
    private Path resolveSkillPath(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        Path resolved = SKILLS_PATH.resolve(skillName + ".md").normalize();
        if (!resolved.startsWith(SKILLS_PATH.normalize())) {
            log.warn("Path traversal attempt blocked: {}", skillName);
            return null;
        }
        return resolved;
    }

    /**
     * 创建新的技能文档，生成包含 YAML front matter 的 Markdown 文件。
     * <p>
     * 版本号初始为 1，评分初始为 0。如果文件已存在则被覆盖。
     *
     * @param name        技能名称（kebab-case 格式）
     * @param description 一句话描述
     * @param triggers    触发关键词列表
     * @param content     技能正文（Markdown 格式）
     */
    public synchronized void createSkill(String name, String description, List<String> triggers, String content) {
        if (!initialized) return;
        if (name == null || name.isBlank()) {
            log.error("Skill name must not be null or blank");
            return;
        }
        Path skillFile = resolveSkillPath(name);
        if (skillFile == null) return;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String triggersStr = triggers.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(", ", "[", "]"));

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

        try {
            Files.writeString(skillFile, skillMd);
            log.info("Skill created: {}", name);
        } catch (IOException e) {
            log.error("Failed to create skill: {}", name, e);
        }
    }

    /**
     * 通过关键词匹配触发条件来查找相关技能。
     * <p>
     * 遍历所有技能文件的 trigger 字段，当用户查询中包含任意触发词时视为匹配。
     * 匹配是大小写不敏感的。
     *
     * @param query 用户查询文本
     * @return 技能名称到技能正文的映射，无匹配时返回空 Map
     */
    public synchronized Map<String, String> match(String query) {
        if (!initialized) return Collections.emptyMap();
        Map<String, String> matched = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return matched;
        }
        String lowerQuery = query.toLowerCase();
        try {
            if (!Files.exists(SKILLS_PATH)) return matched;
            List<Path> skillFiles;
            try (var stream = Files.list(SKILLS_PATH)) {
                skillFiles = stream
                        .filter(p -> p.toString().endsWith(".md"))
                        .collect(Collectors.toList());
            }

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

    /** 用于匹配 YAML front matter 中 score 字段的正则模式 */
    private static final Pattern SCORE_PATTERN = Pattern.compile("score: (\\d+)");

    /**
     * 将指定技能的使用评分加 1。
     * <p>
     * 评分用于衡量技能的使用频率，每次匹配命中时调用此方法。
     *
     * @param skillName 技能名称
     */
    public synchronized void incrementScore(String skillName) {
        if (!initialized) return;
        if (skillName == null || skillName.isBlank()) return;
        Path skillFile = resolveSkillPath(skillName);
        if (skillFile == null || !Files.exists(skillFile)) return;
        try {
            String content = Files.readString(skillFile);
            Matcher matcher = SCORE_PATTERN.matcher(content);
            if (matcher.find()) {
                int newScore = Integer.parseInt(matcher.group(1)) + 1;
                String updated = matcher.replaceFirst("score: " + newScore);
                Files.writeString(skillFile, updated);
            }
        } catch (IOException e) {
            log.error("Failed to increment score for: {}", skillName, e);
        }
    }

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

    /**
     * 更新技能内容，创建版本备份后覆盖主文件。
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

            // 3. 重写主文件
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

    /**
     * 列出所有已存储的技能信息。
     *
     * @return 技能信息列表，无技能时返回空列表
     */
    public synchronized List<SkillInfo> listAll() {
        if (!initialized) return Collections.emptyList();
        List<SkillInfo> skills = new ArrayList<>();
        try {
            if (!Files.exists(SKILLS_PATH)) return skills;
            try (var stream = Files.list(SKILLS_PATH)) {
                List<Path> skillFiles = stream
                        .filter(p -> p.toString().endsWith(".md"))
                        .collect(Collectors.toList());
                for (Path skillFile : skillFiles) {
                    String content = Files.readString(skillFile);
                    SkillInfo info = parseFrontMatter(content);
                    if (info != null) skills.add(info);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list skills", e);
        }
        return skills;
    }

    /**
     * 根据技能名称获取完整的技能文档内容。
     *
     * @param skillName 技能名称
     * @return 技能文档的完整 Markdown 内容，不存在时返回 null
     */
    public synchronized String getByName(String skillName) {
        if (!initialized) return null;
        Path skillFile = resolveSkillPath(skillName);
        if (skillFile == null || !Files.exists(skillFile)) return null;
        try {
            return Files.readString(skillFile);
        } catch (IOException e) {
            log.error("Failed to read skill: {}", skillName, e);
            return null;
        }
    }

    /**
     * 解析技能文档中的 YAML front matter，提取元数据。
     * <p>
     * 支持的 front matter 字段：name、description、version、score、created、triggers。
     *
     * @param content 完整的技能文档内容
     * @return 解析后的 SkillInfo 对象，解析失败或格式不正确时返回 null
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
            }
        }
        return info;
    }

    /**
     * 提取 YAML front matter 之后的正文内容。
     *
     * @param content 完整的技能文档内容
     * @return front matter 之后的正文，无 front matter 时返回原文
     */
    String extractBody(String content) {
        int end = content.indexOf("---", 3);
        if (end == -1) return content;
        return content.substring(end + 3).trim();
    }

    /**
     * 语义去重检查——通过 LLM 判断候选 skill 是否与已有 skill 高度重复。
     * 如果 LLM 调用失败，默认返回 false（fail-open：不阻止创建）。
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
}
