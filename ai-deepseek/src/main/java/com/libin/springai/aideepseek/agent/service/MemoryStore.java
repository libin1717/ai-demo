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

/**
 * 持久记忆存储服务，基于本地文件系统管理 Agent 的记忆。
 * <p>
 * 记忆分为三类文件存储：
 * <ul>
 *   <li>facts.md — 关于项目的客观事实</li>
 *   <li>profile.md — 用户的编码偏好</li>
 *   <li>decisions.md — 技术决策记录</li>
 * </ul>
 * MEMORY.md 作为索引文件记录所有记忆条目的时间戳和类型。
 * 所有公共方法使用 synchronized 保证线程安全。
 */
@Slf4j
@Service
public class MemoryStore {

    /** 记忆存储根目录路径 */
    static final Path MEMORY_PATH = Paths.get(".memory");

    /** 记忆数据文件列表 */
    private static final List<String> MEMORY_FILES = List.of("facts.md", "profile.md", "decisions.md");

    /**
     * 构造 MemoryStore 实例，初始化 .memory 目录结构。
     * <p>
     * 自动创建必要的子目录和默认索引文件，已存在的文件不会被覆盖。
     * 初始化失败时记录错误日志但不阻止服务启动。
     */
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

    /**
     * 保存一条客观事实到 facts.md。
     *
     * @param fact 要保存的事实文本
     */
    public synchronized void saveFact(String fact) {
        appendToFile(MEMORY_PATH.resolve("facts.md"), "- " + fact + "\n");
        appendToIndex("fact", fact);
    }

    /**
     * 保存一条用户偏好到 profile.md。
     *
     * @param preference 要保存的偏好文本
     */
    public synchronized void savePreference(String preference) {
        appendToFile(MEMORY_PATH.resolve("profile.md"), "- " + preference + "\n");
        appendToIndex("preference", preference);
    }

    /**
     * 保存一条技术决策到 decisions.md。
     *
     * @param decision 要保存的决策文本
     */
    public synchronized void saveDecision(String decision) {
        appendToFile(MEMORY_PATH.resolve("decisions.md"), "- " + decision + "\n");
        appendToIndex("decision", decision);
    }

    /**
     * 追加一行内容到指定文件末尾。
     *
     * @param filePath 目标文件路径
     * @param line     要追加的行内容
     */
    private void appendToFile(Path filePath, String line) {
        try {
            Files.writeString(filePath, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to append to {}", filePath, e);
        }
    }

    /**
     * 向 MEMORY.md 索引文件中追加一条记录。
     *
     * @param type    记录类型（fact、preference、decision）
     * @param content 记录内容（超过 80 字符会自动截断）
     */
    private void appendToIndex(String type, String content) {
        String timestamp = LocalDateTime.now().toString().substring(0, 19);
        String truncated = content.length() > 80 ? content.substring(0, 80) + "..." : content;
        appendToFile(MEMORY_PATH.resolve("MEMORY.md"),
                "- [" + timestamp + "] " + type + ": " + truncated + "\n");
    }

    /**
     * 按关键字搜索记忆，返回文件名到匹配行列表的映射。
     * <p>
     * 搜索结果分为精确匹配（完整单词）和部分匹配（子串），精确匹配排在前面。
     *
     * @param query 搜索关键字
     * @return 文件名到匹配行列表的映射，key 为文件名，value 为匹配的行内容列表；无结果时返回空 Map
     */
    public synchronized Map<String, List<String>> search(String query) {
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

    /**
     * 获取记忆统计摘要，返回各记忆文件的条目数量。
     *
     * @return 文件名到条目数量的映射
     */
    public synchronized Map<String, Integer> getMemorySummary() {
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

    /**
     * 读取指定记忆文件的完整内容。
     *
     * @param fileName 文件名（相对于 .memory 目录），如 "facts.md"
     * @return 文件内容字符串，文件不存在或路径越出时返回 null
     */
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
