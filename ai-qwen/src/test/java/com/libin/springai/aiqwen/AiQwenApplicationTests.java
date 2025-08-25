package com.libin.springai.aiqwen;

import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageModel;
import com.alibaba.cloud.ai.dashscope.image.DashScopeImageOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class AiQwenApplicationTests {


    /**
     * 阿里通义千问 聊天
     */
    @Test
    public void testQwen(@Autowired DashScopeChatModel dashScopeChatModel) {

        String content = dashScopeChatModel.call("你好你是谁");
        System.out.println(content);
    }

    /**
     * 文生图
     * <p>
     * 调用通义万象 wanx2.1-t2i-turbo
     * <p>
     * 先获取任务ID
     * 在获取任务详情
     * 根据任务状态，获取生成的图片URL;
     */
    @Test
    public void text2Img(@Autowired DashScopeImageModel imageModel) {

        ImagePrompt imagePrompt = new ImagePrompt("程序员李彬",
                DashScopeImageOptions.builder()
                        .withModel(DashScopeImageApi.ImageModel.WANX2_1_T2I_TURBO.value).build()
        );

        /*创建任务获取任务ID*/
        String task = imageModel.submitImageGenTask(imagePrompt);

        int retryNum = 0;
        while (true) {

            if (3 <= retryNum) {
                System.out.println("生成图片失败");
                break;
            }
            /*根据任务ID查询结果*/
            DashScopeImageApi.DashScopeImageAsyncResponse imageGenTask = imageModel.getImageGenTask(task);

            /*
             * PENDING：任务排队中
             * RUNNING：任务处理中
             * SUCCEEDED：任务执行成功
             * FAILED：任务执行失败
             * CANCELED：任务取消成功
             * UNKNOWN：任务不存在或状态未知
             */
            if ("SUCCEEDED".equals(imageGenTask.output().taskStatus())) {
                List<DashScopeImageApi.DashScopeImageAsyncResponse.DashScopeImageAsyncResponseResult> results = imageGenTask.output().results();
                for (DashScopeImageApi.DashScopeImageAsyncResponse.DashScopeImageAsyncResponseResult result : results) {
                    // 图片url
                    System.out.println(" -- 生成图片成功URL地址如下 -- ");
                    System.out.println(result.url());
                }
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            retryNum++;

        }


    }

}
