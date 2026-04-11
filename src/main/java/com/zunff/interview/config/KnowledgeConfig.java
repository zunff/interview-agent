package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库配置类
 */
@Data
@Configuration
public class KnowledgeConfig {

    @Value("${interview.knowledge.enabled:true}")
    private boolean enabled;
}
