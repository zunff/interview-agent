package com.zunff.interview.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import com.zunff.interview.service.MultimodalAnalysisService;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.service.VideoStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
     * 文本模型 ChatClient (qwen-plus)
     * 用于文本评估和语音情感分析
     */
    @Bean
    public ChatClient textChatClient(ChatModel chatModel) {
        log.info("初始化文本 ChatClient，模型: qwen-plus");
        return ChatClient.create(chatModel);
    }

    /**
     * 视觉模型 ChatClient (qwen3.5-omni-plus)
     * 用于视频帧分析，独立 Bean 避免污染文本模型
     */
    @Bean
    public ChatClient visionChatClient(
            ChatModel chatModel,
            @Value("${spring.ai.dashscope.vision.model}") String visionModel) {
        log.info("初始化视觉 ChatClient，模型: {}", visionModel);
        DashScopeChatOptions visionOptions = new DashScopeChatOptions();
        visionOptions.setModel(visionModel);
        return ChatClient.builder(chatModel)
                .defaultOptions(visionOptions)
                .build();
    }

    /**
     * 多模态分析服务
     * 注入独立的 ChatClient Bean，避免 Builder 共享污染
     */
    @Bean
    public MultimodalAnalysisService multimodalAnalysisService(
            ChatClient textChatClient,
            ChatClient visionChatClient,
            AudioTranscriptionModel transcriptionModel,
            PromptTemplateService promptTemplateService,
            @Value("${interview.multimodal.enabled:true}") boolean multimodalEnabled) {
        return new MultimodalAnalysisService(
                textChatClient,
                visionChatClient,
                transcriptionModel,
                promptTemplateService,
                multimodalEnabled);
    }

    @Bean
    public VideoStreamService videoStreamService(MultimodalAnalysisService multimodalAnalysisService) {
        return new VideoStreamService(multimodalAnalysisService);
    }
}
