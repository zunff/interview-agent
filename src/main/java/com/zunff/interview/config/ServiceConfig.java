package com.zunff.interview.config;

import com.zunff.interview.service.MultimodalAnalysisService;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.service.VideoStreamService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务配置类
 */
@Configuration
public class ServiceConfig {

    @Bean
    public PromptTemplateService promptTemplateService() {
        return new PromptTemplateService();
    }

    @Bean
    public MultimodalAnalysisService multimodalAnalysisService(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService) {
        return new MultimodalAnalysisService(chatClientBuilder, promptTemplateService);
    }

    @Bean
    public VideoStreamService videoStreamService(MultimodalAnalysisService multimodalAnalysisService) {
        return new VideoStreamService(multimodalAnalysisService);
    }
}
