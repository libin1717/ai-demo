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
