package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangGraph4j 图配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "interview.graph")
public class GraphConfigProperties {

    /**
     * 主图最大递归深度（防止无限循环）
     */
    private int mainRecursionLimit = 25;

}
