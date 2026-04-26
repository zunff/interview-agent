package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 多模态分析配置类
 */
@Data
@Configuration
@ConfigurationProperties("interview.multimodal")
public class MultimodalConfig {

    private boolean enabled;

    private String apiKey;
}
