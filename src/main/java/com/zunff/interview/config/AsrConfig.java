package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ASR 实时语音识别配置类
 * 使用 DashScope SDK + qwen3-asr-flash-realtime
 */
@Data
@Configuration
public class AsrConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.asr.model:qwen3-asr-flash-realtime}")
    private String model;

    @Value("${spring.ai.dashscope.asr.url:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}")
    private String url;

    @Value("${spring.ai.dashscope.asr.language:zh}")
    private String language;

    @Value("${spring.ai.dashscope.asr.input-audio-format:pcm}")
    private String inputAudioFormat;

    @Value("${spring.ai.dashscope.asr.sample-rate:16000}")
    private int sampleRate;
}
