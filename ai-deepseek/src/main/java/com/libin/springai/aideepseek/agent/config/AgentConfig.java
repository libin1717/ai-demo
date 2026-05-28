package com.libin.springai.aideepseek.agent.config;

import com.libin.springai.aideepseek.agent.tool.AgentTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public AgentTools agentTools() {
        return new AgentTools();
    }
}
