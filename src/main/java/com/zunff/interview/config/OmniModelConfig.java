package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 全模态模型配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "interview.multimodal.omni")
public class OmniModelConfig {

    private String model = "qwen3.5-omni-plus";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
}
