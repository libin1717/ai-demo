package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能信息 DTO，包含技能的名称、描述、触发条件、版本、评分和创建时间。
 * 技能由系统在工具调用次数达到阈值时自动从对话中沉淀生成。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillInfo {

    /** 技能名称（kebab-case 格式，如 spring-controller-pattern） */
    private String name;

    /** 技能的一句话描述 */
    private String description;

    /** 触发关键词列表，用户消息包含这些关键词时匹配该技能 */
    private List<String> triggers;

    /** 技能版本号，每次更新自增 */
    private int version;

    /** 技能使用评分，每次匹配命中时递增，值越高表示越常用 */
    private int score;

    /** 技能创建时间（ISO 8601 格式），如 2025-08-25T10:30:00 */
    private String created;
}
