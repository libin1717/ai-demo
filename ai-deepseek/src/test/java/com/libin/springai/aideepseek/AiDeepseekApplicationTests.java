package com.libin.springai.aideepseek;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
class AiDeepseekApplicationTests {

    /**
     * deepseek-chat 非思考模型
     * 阻塞式对话
     */
    @Test
    void chatTest(@Autowired DeepSeekChatModel chatModel) {
        String call = chatModel.call("你好，请为LiBin写一首诗");
        System.out.println(call);

    }

    /**
     * deepseek-chat 非思考模型
     *
     * 流式对话
     */
    @Test
    void chatTest2(@Autowired DeepSeekChatModel chatModel) {

        Flux<String> stream = chatModel.stream("你好，请为LiBin写一首诗");
        stream.toIterable().forEach(System.out::println);
    }


    /* deepseek-reasoner 思考型对话*/


    /**
     * temperature 温度： 0-2 浮点数值，值越高 创作性越强
     *
     * 官网推荐配置如下
     * 代码生成/数学解题	0.0
     * 数据抽取/分析	1.0
     * 通用对话	1.3
     * 翻译	1.3
     * 创意类写作/诗歌创作	1.5
     *
     * 配置文件 spring.ai.deepseek.chat.options.temperature=0.8
     *
     */
    @Test
    void testChatOptions(@Autowired DeepSeekChatModel chatModel) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .temperature(1.9d)
                /*模型类型*/
                .model("deepseek-reasoner")
                .build();
        ChatResponse res = chatModel.call(new Prompt("你好，请为LiBin写一首诗", options));
        System.out.println(res.getResult().getOutput().getText());
    }

}
