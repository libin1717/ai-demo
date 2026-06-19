package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 单次执行周期的响应结果，包含对话回复、工具调用统计、记忆使用情况和技能沉淀状态。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {

    /** AI 生成的回复文本 */
    private String reply;

    /** 本次对话中工具调用的总次数 */
    private int toolCallCount;

    /** 本次对话中使用的记忆统计，key 为记忆文件名，value 为匹配的记忆条数 */
    private Map<String, Integer> memoriesUsed;

    /** 本次对话是否触发了新技能的生成（工具调用次数达到阈值时触发） */
    private boolean skillTriggered;

    /** 本次对话触发生成的新技能名称，未触发时为 null */
    private String newSkillName;

    /** 本次对话新增的记忆条目数量（提取的新事实 + 偏好 + 决策） */
    private int newMemoryCount;

    /** 本轮对话中匹配到的技能名称列表，无匹配时为空列表 */
    private List<String> skillsMatched;
}
