package com.zunff.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "interview.resume-analysis")
public class ResumeAnalysisConfig {

    private int recursionLimit = 25;
}
