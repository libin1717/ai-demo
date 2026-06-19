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
    void testGetRecentMatches() throws InterruptedException {
        tracker.recordMatch("recent-skill", List.of("a"), "Message 1", 1);
        Thread.sleep(2);
        tracker.recordMatch("recent-skill", List.of("b"), "Message 2", 2);
        Thread.sleep(2);
        tracker.recordMatch("recent-skill", List.of("c"), "Message 3", 3);
        Thread.sleep(2);
        tracker.recordMatch("recent-skill", List.of("d"), "Message 4", 4);
        Thread.sleep(2);
        tracker.recordMatch("recent-skill", List.of("e"), "Message 5", 5);
        Thread.sleep(2);
        tracker.recordMatch("recent-skill", List.of("f"), "Message 6", 6);

        List<SkillUsageRecord> recent = tracker.getRecentMatches("recent-skill", 3);
        assertEquals(3, recent.size());
        assertTrue(recent.get(0).getUserMessageDigest().contains("Message 6"));
    }

    @Test
    void testEmptySkillReturnsEmpty() {
        assertTrue(tracker.queryUsage("nonexistent", null, null).isEmpty());
        assertEquals(0, tracker.countSuccessfulMatches("nonexistent"));
        assertEquals(0, tracker.countMatches("nonexistent"));
    }
}
