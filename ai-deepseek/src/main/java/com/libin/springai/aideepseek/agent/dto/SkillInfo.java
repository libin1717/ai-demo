package com.libin.springai.aideepseek.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillInfo {

    private String name;
    private String description;
    private List<String> triggers;
    private int version;
    private int score;
    private String created;
}
