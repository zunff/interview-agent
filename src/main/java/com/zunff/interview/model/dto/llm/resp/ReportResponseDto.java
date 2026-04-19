package com.zunff.interview.model.dto.llm.resp;

/**
 * 面试报告生成响应
 */
public record ReportResponseDto(
        String candidateOverview,
        String technicalCapability,
        String overallCompetency,
        String performanceSummary,
        String hiringRecommendation
) {
    /**
     * 生成完整的 Markdown 格式总评内容
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();

        if (candidateOverview != null && !candidateOverview.isEmpty()) {
            sb.append(candidateOverview).append("\n\n");
        }
        if (technicalCapability != null && !technicalCapability.isEmpty()) {
            sb.append(technicalCapability).append("\n\n");
        }
        if (overallCompetency != null && !overallCompetency.isEmpty()) {
            sb.append(overallCompetency).append("\n\n");
        }
        if (performanceSummary != null && !performanceSummary.isEmpty()) {
            sb.append(performanceSummary).append("\n\n");
        }

        return sb.toString().trim();
    }

    /**
     * 获取招聘推荐的中文显示格式
     */
    public String formatRecommendation() {
        String recommendation = hiringRecommendation;
        if (recommendation == null || recommendation.isEmpty()) {
            recommendation = "hold";
        }

        String displayText = switch (recommendation.trim().toLowerCase()) {
            case "strongly_recommend" -> "强烈推荐";
            case "recommend" -> "推荐";
            case "hold" -> "待定";
            case "not_recommend" -> "不推荐";
            default -> "模型没有输出录用建议";
        };

        return "**招聘建议：** " + displayText;
    }
}
