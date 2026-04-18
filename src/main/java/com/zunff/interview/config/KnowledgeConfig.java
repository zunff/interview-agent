package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "interview.knowledge")
public class KnowledgeConfig {

    private boolean enabled;
}
