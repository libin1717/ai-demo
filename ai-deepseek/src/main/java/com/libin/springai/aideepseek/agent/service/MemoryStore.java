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
     * Search memories by keyword. Returns filename → matching lines.
     * Exact word matches sorted before partial matches.
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
