package com.zunff.interview.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 视频流配置类
 */
@Data
@Configuration
public class VideoConfig {

    @Value("${interview.video.analysis-interval:5000}")
    private int analysisInterval;

    @Value("${interview.video.max-frames-per-analysis:10}")
    private int maxFramesPerAnalysis;
}
