package com.libin.springai.aideepseek.agent.config;

import com.libin.springai.aideepseek.agent.tool.AgentTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 模块的 Spring 配置类，负责注册 Agent 相关的 Bean。
 */
@Configuration
public class AgentConfig {

    /**
     * 注册 AgentTools Bean，为 LLM 工具调用提供文件读写、代码搜索和目录列表能力。
     *
     * @return AgentTools 实例
     */
    @Bean
    public AgentTools agentTools() {
        return new AgentTools();
    }
}
