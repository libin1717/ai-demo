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
            try (var stream = Files.walk(memPath)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
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
    void shouldRankByMultipleKeywordHits() {
        memoryStore.saveFact("项目使用 Spring Boot 3.5.5 框架");
        memoryStore.saveFact("使用 @Valid 和 @Size 注解做参数校验");
        memoryStore.saveFact("端口号是 8091");

        // "Spring" hits line 1, "注解" hits line 2, "Docker" hits nothing
        Map<String, List<String>> results = memoryStore.searchByKeywords(
                List.of("Spring", "注解", "Docker"));
        assertEquals(1, results.size());
        List<String> matches = results.get("facts.md");
        assertEquals(2, matches.size());
    }

    @Test
    void shouldRankLineWithMoreKeywordHitsFirst() {
        // Line 1: contains "Spring" and "参数校验" → 2 hits
        // Line 2: contains "注解" → 1 hit
        // Line 1 should rank FIRST (2 hits > 1 hit)
        memoryStore.saveFact("使用 Spring Boot 框架做参数校验");
        memoryStore.saveFact("@NotBlank 注解用于验证非空字符串");

        Map<String, List<String>> results = memoryStore.searchByKeywords(
                List.of("Spring", "注解", "参数校验"));

        assertEquals(1, results.size());
        List<String> matches = results.get("facts.md");
        assertEquals(2, matches.size());
        assertEquals("- 使用 Spring Boot 框架做参数校验", matches.get(0));
    }

    @Test
    void shouldReturnEmptyForNoKeywordMatches() {
        memoryStore.saveFact("项目使用 Spring Boot");
        Map<String, List<String>> results = memoryStore.searchByKeywords(
                List.of("Docker", "Kubernetes"));
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldHandleEmptyKeywordList() {
        memoryStore.saveFact("项目使用 Spring Boot");
        Map<String, List<String>> results = memoryStore.searchByKeywords(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldHandleNullKeywordList() {
        memoryStore.saveFact("项目使用 Spring Boot");
        Map<String, List<String>> results = memoryStore.searchByKeywords(null);
        assertTrue(results.isEmpty());
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
