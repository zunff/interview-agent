package com.zunff.interview.model.dto.llm.resp;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 题目规划响应 DTO
 * QuestionPlanningNode 调用 LLM 后解析出的 JSON 结果
 * 包含每类题目的主题分配、禁止话题、难度分配
 */
public record QuestionPlanResponseDto(
        PlanItem technicalPlan,
        PlanItem projectPlan,
        PlanItem businessPlan,
        PlanItem softSkillPlan,
        String planSummary
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public record PlanItem(
            List<String> assignedTopics,
            List<String> forbiddenTopics,
            Map<String, Integer> difficultyAllocation
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 获取指定题型的规划
     */
    public PlanItem getPlanForType(String promptName) {
        return switch (promptName) {
            case "technical" -> technicalPlan;
            case "project" -> projectPlan;
            case "business" -> businessPlan;
            case "soft" -> softSkillPlan;
            default -> null;
        };
    }

    /**
     * 获取指定题型的规划文本（注入 prompt 用）
     */
    public String getPlanContextForType(String promptName) {
        PlanItem plan = getPlanForType(promptName);
        if (plan == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("## 出题规划约束\n\n");
        sb.append("### 必出话题（请只在这些范围内出题）\n");
        if (plan.assignedTopics() != null && !plan.assignedTopics().isEmpty()) {
            for (String topic : plan.assignedTopics()) {
                sb.append("- ").append(topic).append("\n");
            }
        }
        if (plan.forbiddenTopics() != null && !plan.forbiddenTopics().isEmpty()) {
            sb.append("\n### 禁止话题（已在其他题目类型中出现，请勿重复）\n");
            for (String topic : plan.forbiddenTopics()) {
                sb.append("- ").append(topic).append("\n");
            }
        }
        if (plan.difficultyAllocation() != null && !plan.difficultyAllocation().isEmpty()) {
            sb.append("\n### 难度分配\n");
            for (Map.Entry<String, Integer> entry : plan.difficultyAllocation().entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" 题\n");
            }
        }
        sb.append("\n### 整体规划说明\n").append(planSummary != null ? planSummary : "");
        return sb.toString();
    }
}
