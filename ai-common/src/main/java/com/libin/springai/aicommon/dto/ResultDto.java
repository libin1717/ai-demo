package com.libin.springai.aicommon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * @Author libin
 * @Data 2025/8/25 22:27
 * @Version 1.0
 * @Description
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultDto<T> {

    private Integer code;

    private String message;

    private T data;


    /**
     * @param data 结果
     * @param <T>  类型
     * @return 成功
     */
    public static <T> ResultDto<T> success(T data) {
        return new ResultDto<>(HttpStatus.OK.value(), HttpStatus.OK.getReasonPhrase(), data);
    }


    /**
     * @param <T> 结果
     * @return 失败
     */
    public static <T> ResultDto<T> fail() {
        return new ResultDto<>(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), null);
    }
}
