package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {

    private String reply;

    private int toolCallCount;

    private Map<String, Integer> memoriesUsed;

    private boolean skillTriggered;

    private String newSkillName;

    private int newMemoryCount;
}
