package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 从对话中提取的记忆结构，包含事实、偏好和决策三类信息。
 * 用作 LLM 记忆提取提示的 JSON 反序列化目标。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MemoryExtraction {

    /** 关于项目的客观事实列表（如框架、端口、包名等） */
    private List<String> facts;

    /** 用户的编码偏好列表（如注解风格、命名规范等） */
    private List<String> preferences;

    /** 用户或 Agent 做出的技术决策列表（含选型理由） */
    private List<String> decisions;
}
