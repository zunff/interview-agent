package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * TTS 语音合成配置类
 */
@Data
@Configuration
public class TtsConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.tts.model:qwen3-tts-flash}")
    private String model;

    @Value("${spring.ai.dashscope.tts.voice:Cherry}")
    private String voice;

    @Value("${interview.tts.enabled:true}")
    private boolean enabled;
}
