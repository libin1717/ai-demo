package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Skill 使用追踪记录 DTO，对应 JSONL 文件中的每一行。
 * 记录类型包括：match（匹配）、outcome（结果）、created（创建）。
 * 纯内部使用，不暴露于 API 响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillUsageRecord {

    /** ISO 8601 时间戳 */
    private String timestamp;

    /** 记录类型: "match" | "outcome" | "created" */
    private String type;

    /** 技能名称 */
    private String skillName;

    /** 匹配时命中的触发关键词（仅 match/created 类型） */
    private List<String> triggers;

    /** 用户消息摘要，截取前 200 字符（仅 match 类型） */
    private String userMessageDigest;

    /** 本次对话的工具调用次数 */
    private int toolCallCount;

    /** 本次对话新增的记忆条目数 */
    private int newMemoryCount;

    /** 执行结果: "success" | "failure"（仅 outcome 类型） */
    private String result;

    /** 错误信息，截取前 200 字符（仅 outcome 类型且 result=failure） */
    private String errorMessage;

    /** 技能描述（仅 created 类型） */
    private String description;
}
