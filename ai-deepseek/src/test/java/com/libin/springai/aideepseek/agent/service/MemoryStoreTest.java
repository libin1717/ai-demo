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
        memoryStore.saveFact("项目使用了JavaScript框架进行开发");

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
