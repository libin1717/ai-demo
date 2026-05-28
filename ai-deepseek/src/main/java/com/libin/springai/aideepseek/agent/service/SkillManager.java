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

@Slf4j
@Service
public class SkillManager {

    static final Path SKILLS_PATH = Paths.get(".memory/skills");

    private volatile boolean initialized = true;

    public SkillManager() {
        try {
            Files.createDirectories(SKILLS_PATH);
        } catch (IOException e) {
            log.error("Failed to create skills directory", e);
            initialized = false;
        }
    }

    /**
     * Resolve and validate a skill file path, protecting against path traversal.
     * Returns null if the name is invalid or attempts to escape SKILLS_PATH.
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
     * Create a skill document with YAML front matter
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
                "created: " + timestamp + "\n" +
                "score: 0\n" +
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
     * Match skills by keyword matching against triggers field
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

    private static final Pattern SCORE_PATTERN = Pattern.compile("score: (\\d+)");

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
     * Parse YAML front matter from a skill document
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
     * Extract body content after YAML front matter
     */
    private String extractBody(String content) {
        int end = content.indexOf("---", 3);
        if (end == -1) return content;
        return content.substring(end + 3).trim();
    }
}
