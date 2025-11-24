package com.libin.springai.aideepseek.dto;

import com.libin.springai.aideepseek.common.PromptConstants;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * {@code @User} libin
 * {@code @Data} 2025/11/21
 * {@code @Version} 1.0
 * {@code @Description}
 */
@Data
public class MsgInfoDTO {

    @NotNull
    private String message;
    @NotNull
    private String clientId;

    private String promptType;
}
