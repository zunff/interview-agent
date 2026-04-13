package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ASR 实时语音识别配置类
 * 使用 DashScope SDK Recognition + fun-asr-realtime
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "interview.multimodal.asr")
public class AsrConfig {

    private String model= "fun-asr-realtime";

    private String url = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

    private String language = "zh";

    private String inputAudioFormat = "pcm";

    private int sampleRate = 16000;

    /** 热词表ID，需在阿里云百炼平台创建热词表后填入 */
    private String vocabularyId;
}
