package com.zunff.interview.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    private boolean hasAnalysis;
    private String analysisId;
    private String radarChartData;
    private String skillsExtracted;
    private String reportMarkdown;
}
