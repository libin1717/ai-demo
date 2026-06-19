package com.libin.springai.aideepseek;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.Objects;

@SpringBootTest
class AiDeepseekApplicationTests {

    /**
     * deepseek-v4-flash 非思考模型
     * 阻塞式对话
     */
    @Test
    void chatTest(@Autowired DeepSeekChatModel chatModel) {
        String call = chatModel.call("请帮我简化下列语句: 你是一位历史学者");
        System.out.println(call);
        ChatResponse chatResponse = chatModel.call(new Prompt("请帮我简化下列语句,并告知我唯一答案: 你是一位历史学者"));
        System.out.printf(chatResponse.getResult().toString());

    }

    /**
     * deepseek-v4-flash 非思考模型
     * <p>
     * 流式对话
     */
    @Test
    void chatTest2(@Autowired DeepSeekChatModel chatModel) {

        Flux<String> stream = chatModel.stream("你好，我是LiBin！很高兴认识你");
        stream.toIterable().forEach(System.out::println);
    }


    /* deepseek-reasoner 思考型对话*/

    /**
     * deepseek-reasoner
     * <p>
     * temperature 温度： 0-2 浮点数值，值越高 创作性越强
     * 官网推荐配置如下
     * 代码生成/数学解题	0.0
     * 数据抽取/分析	1.0
     * 通用对话	1.3
     * 翻译	1.3
     * 创意类写作/诗歌创作	1.5
     * <p>
     * 配置文件 spring.ai.deepseek.chat.options.temperature=0.8
     */
    @Test
    void testChatOptions(@Autowired DeepSeekChatModel chatModel) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .temperature(1.5d)
                /*模型类型 */
                .model("deepseek-reasoner")
                .maxTokens(500)
                .build();
        ChatResponse res = chatModel.call(new Prompt("请帮我简化下列语句,并告知我最优答案: 你是一位历史学者", options));
        DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
        /*思维链内容*/
        String content = output.getReasoningContent();
        System.out.println("-- ReasoningContent --");
        System.out.println(content);

        /*最终回答内容*/
        System.out.println("-- text --");
        System.out.println(output.getText());
    }

    /**
     * deepseek-reasoner
     * <p>
     * 流式输出
     */
    @Test
    void testReasonerStream(@Autowired DeepSeekChatModel chatModel) {
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                /*模型类型*/
                .model("deepseek-reasoner")
                .build();

        Prompt prompt = new Prompt("你好请写一首描写新中国的五言绝句", options);
//        new PromptTemplate()

        Flux<ChatResponse> flux = chatModel.stream(prompt);

        System.out.println("-- ReasoningContent --");
        flux.toIterable().forEach(
                res -> {
                    DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                    /*长连接未提供响应内容时，会返回 null*/
                    if (Objects.nonNull(output.getReasoningContent())) {
                        System.out.println(output.getReasoningContent());
                    }
                }
        );

        System.out.println("-- text --");
        flux.toIterable().forEach(
                res -> {
                    DeepSeekAssistantMessage output = (DeepSeekAssistantMessage) res.getResult().getOutput();
                    if (Objects.nonNull(output.getText())) {
                        System.out.println(output.getText());
                    }
                }
        );

    }


}
