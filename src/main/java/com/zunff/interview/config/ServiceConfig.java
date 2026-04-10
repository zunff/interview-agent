package com.zunff.interview.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import com.zunff.interview.service.MultimodalAnalysisService;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.service.VideoStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
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
     * 多模态分析服务
     * 使用 Spring AI Alibaba：
     * - chatClientBuilder: 文本评估 + 语音情感分析 (qwen-plus)
     * - visionChatClientBuilder: 视频帧分析 (qwen-image-2.0-pro)
     * - transcriptionModel: 语音转录 ASR (qwen3-asr-flash-realtime)
     */
    @Bean
    public MultimodalAnalysisService multimodalAnalysisService(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService,
            AudioTranscriptionModel transcriptionModel,
            @Value("${spring.ai.dashscope.vision.model}") String visionModel) {

        // 视觉模型 Builder
        log.info("初始化视觉模型，模型名称: {}", visionModel);
        DashScopeChatOptions visionOptions = new DashScopeChatOptions();
        visionOptions.setModel(visionModel);
        ChatClient.Builder visionBuilder = chatClientBuilder
                .defaultOptions(visionOptions);

        return new MultimodalAnalysisService(
                chatClientBuilder,
                visionBuilder,
                transcriptionModel,
                promptTemplateService);
    }

    @Bean
    public VideoStreamService videoStreamService(MultimodalAnalysisService multimodalAnalysisService) {
        return new VideoStreamService(multimodalAnalysisService);
    }
}
