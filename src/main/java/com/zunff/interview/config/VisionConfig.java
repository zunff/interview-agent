package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 视觉分析配置类
 * 使用 DashScope SDK 调用 Qwen-Omni 模型
 */
@Data
@Configuration
public class VisionConfig {

    @Value("${spring.ai.dashscope.vision.model}")
    private String model;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.vision.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;
}
