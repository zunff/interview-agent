package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TTS 语音合成配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "interview.tts")
public class TtsConfig {

    private String model = "qwen3-tts-flash-realtime";

    private String voice = "Ethan";

    private String url = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";

    private boolean enabled = false;
}
