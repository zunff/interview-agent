package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Prompt behavior configuration.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "interview.prompt")
public class PromptConfig {

    /**
     * Output language for candidate-facing generated content.
     * Examples: zh-CN, en-US.
     */
    private String responseLanguage = "zh-CN";
}

