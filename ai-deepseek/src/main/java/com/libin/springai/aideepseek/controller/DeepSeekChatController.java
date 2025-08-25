package com.libin.springai.aideepseek.controller;

//import org.springframework.ai.chat.client.ChatClient;
import com.libin.springai.aicommon.dto.ResultDto;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author libin
 * @Data 2025/8/24 20:35
 * @Version 1.0
 * @Description
 */

@RestController
@RequestMapping("/deepseek/chat")
public class DeepSeekChatController {

    @Autowired
    private DeepSeekChatModel deepSeekChatModel;

    @GetMapping("/deepseekChatCall")
    public ResultDto<Object> simpleChat2(@RequestParam String message) {
        String content = deepSeekChatModel.call(message);
        System.out.println(content);
        return ResultDto.success(content);
    }


}
