package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 多模态分析配置类
 */
@Data
@Configuration
public class MultimodalConfig {

    @Value("${interview.multimodal.enabled:true}")
    private boolean enabled;
}
