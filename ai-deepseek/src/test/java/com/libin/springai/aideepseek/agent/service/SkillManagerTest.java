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
            try (var stream = Files.walk(skillsPath)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
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

        matched = skillManager.match("帮我写test");
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
