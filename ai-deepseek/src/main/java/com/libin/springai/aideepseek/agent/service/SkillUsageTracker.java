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
import java.time.format.DateTimeFormatter;
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

    /** 毫秒精度时间戳格式，保证同秒内多记录的排序确定性 */
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

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
        record.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FMT));
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
            record.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FMT));
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
        record.setTimestamp(LocalDateTime.now().format(TIMESTAMP_FMT));
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
