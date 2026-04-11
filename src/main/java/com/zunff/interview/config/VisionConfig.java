package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 视觉分析配置类
 */
@Data
@Configuration
public class VisionConfig {

    @Value("${spring.ai.dashscope.vision.model}")
    private String model;
}
