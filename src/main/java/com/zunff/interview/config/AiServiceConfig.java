package com.zunff.interview.config;

import com.zunff.interview.service.extend.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务配置类
 * 统一注册 ASR、TTS、视觉分析等 AI 服务的 Bean
 */
@Slf4j
@Configuration
public class AiServiceConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

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
    public OmniModalService qwenOmniService(OmniModelConfig visionConfig) {
        log.info("初始化 QwenOmniService，模型: {}, baseUrl: {}",
                visionConfig.getModel(), visionConfig.getBaseUrl());
        return new OmniModalService(
                apiKey,
                visionConfig.getModel(),
                visionConfig.getBaseUrl());
    }

    /**
     * ASR 实时语音识别服务 (fun-asr-realtime)
     * 使用 DashScope SDK Recognition 进行流式语音转录
     */
    @Bean
    public AsrRealtimeService asrRealtimeService(AsrConfig asrConfig) {
        log.info("初始化 AsrRealtimeService，模型: {}, url: {}",
                asrConfig.getModel(), asrConfig.getUrl());
        return new AsrRealtimeService(apiKey, asrConfig);
    }

    /**
     * TTS 语音合成服务 (qwen3-tts-flash-realtime)
     * 使用 DashScope SDK QwenTtsRealtime 进行流式语音合成
     */
    @Bean
    public TtsRealtimeService ttsRealtimeService(TtsConfig ttsConfig) {
        log.info("初始化 TtsRealtimeService，模型: {}, voice: {}, enabled: {}",
                ttsConfig.getModel(), ttsConfig.getVoice(), ttsConfig.isEnabled());
        return new TtsRealtimeService(apiKey, ttsConfig);
    }

    /**
     * 多模态分析服务
     * 使用 QwenOmniService (DashScope SDK) 进行视觉分析
     */
    @Bean
    public MultimodalAnalysisService multimodalAnalysisService(
            ChatClient textChatClient,
            OmniModalService qwenOmniService,
            PromptTemplateService promptTemplateService,
            MultimodalConfig multimodalConfig) {
        return new MultimodalAnalysisService(
                textChatClient,
                qwenOmniService,
                promptTemplateService,
                multimodalConfig.isEnabled());
    }

    @Bean
    public VideoStreamService videoStreamService(MultimodalAnalysisService multimodalAnalysisService, VideoConfig videoConfig) {
        return new VideoStreamService(multimodalAnalysisService, videoConfig);
    }
}
