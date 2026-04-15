package com.zunff.interview.config;

import com.zunff.interview.service.extend.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * AI 服务配置类
 * 统一注册 ASR、TTS、视觉分析等 AI 服务的 Bean
 */
@Slf4j
@Configuration
public class AiServiceConfig {

    @Value("${spring.ai.openai.api-key}")
    private String dashscopeApiKey;

    /**
     * 自定义 RestTemplate 以记录请求详情
     */
    @Bean
    public RestTemplateCustomizer restTemplateCustomizer() {
        return restTemplate -> {
            restTemplate.getInterceptors().add((ClientHttpRequestInterceptor) (request, body, execution) -> {
                log.info("=== RestTemplate HTTP Request ===");
                log.info("URI: {}", request.getURI());
                log.info("Method: {}", request.getMethod());
                log.info("Headers: {}", request.getHeaders());
                log.info("Body length: {}", body.length);
                if (body.length > 0 && body.length < 1000) {
                    log.info("Body: {}", new String(body));
                }
                log.info("=================================");
                return execution.execute(request, body);
            });
        };
    }

    /**
     * 自定义 RestClient.Builder 以拦截 Spring AI 的请求
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    log.info("=== RestClient HTTP Request ===");
                    log.info("URI: {}", request.getURI());
                    log.info("Method: {}", request.getMethod());
                    log.info("Headers: {}", request.getHeaders());
                    log.info("Body length: {}", body != null ? body.length : 0);
                    if (body != null && body.length > 0 && body.length < 2000) {
                        log.info("Body: {}", new String(body));
                    }
                    log.info("==================================");
                    return execution.execute(request, body);
                });
    }

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
     * ChatClient.Builder Bean
     * 用于依赖注入到各个 Node
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
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
                dashscopeApiKey,
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
        return new AsrRealtimeService(dashscopeApiKey, asrConfig);
    }

    /**
     * TTS 语音合成服务 (qwen3-tts-flash-realtime)
     * 使用 DashScope SDK QwenTtsRealtime 进行流式语音合成
     */
    @Bean
    public TtsRealtimeService ttsRealtimeService(TtsConfig ttsConfig) {
        log.info("初始化 TtsRealtimeService，模型: {}, voice: {}, enabled: {}",
                ttsConfig.getModel(), ttsConfig.getVoice(), ttsConfig.isEnabled());
        return new TtsRealtimeService(dashscopeApiKey, ttsConfig);
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
            PromptConfig promptConfig,
            MultimodalConfig multimodalConfig) {
        return new MultimodalAnalysisService(
                textChatClient,
                qwenOmniService,
                promptTemplateService,
                promptConfig,
                multimodalConfig.isEnabled());
    }

    @Bean
    public VideoStreamService videoStreamService(VideoConfig videoConfig) {
        return new VideoStreamService(videoConfig);
    }
}
