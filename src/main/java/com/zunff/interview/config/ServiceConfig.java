package com.zunff.interview.config;

import com.zunff.interview.service.extend.AsrRealtimeService;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.service.extend.QwenOmniService;
import com.zunff.interview.service.extend.VideoStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务配置类
 */
@Slf4j
@Configuration
public class ServiceConfig {

    @Bean
    public PromptTemplateService promptTemplateService() {
        return new PromptTemplateService();
    }

    /**
     * 文本模型 ChatClient (qwen-plus)
     * 用于文本评估和语音情感分析
     */
    @Bean
    public ChatClient textChatClient(ChatModel chatModel) {
        log.info("初始化文本 ChatClient，模型: {}", chatModel.getDefaultOptions().getModel());
        return ChatClient.create(chatModel);
    }

    /**
     * Qwen-Omni 多模态服务 (qwen3.5-omni-plus)
     * 使用 DashScope OpenAI 兼容 API，用于视频帧分析
     */
    @Bean
    public QwenOmniService qwenOmniService(VisionConfig visionConfig) {
        log.info("初始化 QwenOmniService，模型: {}, baseUrl: {}",
                visionConfig.getModel(), visionConfig.getBaseUrl());
        return new QwenOmniService(
                visionConfig.getApiKey(),
                visionConfig.getModel(),
                visionConfig.getBaseUrl());
    }

    /**
     * 多模态分析服务
     * 使用 QwenOmniService (DashScope SDK) 进行视觉分析
     */
    @Bean
    public MultimodalAnalysisService multimodalAnalysisService(
            ChatClient textChatClient,
            QwenOmniService qwenOmniService,
            AsrRealtimeService asrRealtimeService,
            PromptTemplateService promptTemplateService,
            MultimodalConfig multimodalConfig) {
        return new MultimodalAnalysisService(
                textChatClient,
                qwenOmniService,
                asrRealtimeService,
                promptTemplateService,
                multimodalConfig.isEnabled());
    }

    @Bean
    public VideoStreamService videoStreamService(MultimodalAnalysisService multimodalAnalysisService, VideoConfig videoConfig) {
        return new VideoStreamService(multimodalAnalysisService, videoConfig);
    }
}
