package com.zunff.interview.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarChartData {

    private List<DimensionScore> dimensions;
    private int overallScore;
    private String overallLevel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionScore {
        private String name;
        private String key;
        private int score;
        private String comment;
    }
}
