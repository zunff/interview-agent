package com.zunff.interview.model.dto;

import lombok.*;

import java.io.Serializable;

/**
 * 岗位分析结果
 * 用于存储岗位类型分析和题目分配结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAnalysisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 岗位类型
     */
    @Getter
    public enum JobType {
        /**
         * 技术驱动型：技术要求高，技术问题占比高
         */
        TECHNICAL_DRIVEN(1, "technical_driven", "技术驱动型"),

        /**
         * 业务驱动型：业务要求高，业务问题占比高
         */
        BUSINESS_DRIVEN(2, "business_driven", "业务驱动型"),

        /**
         * 均衡型：技术与业务要求相当
         */
        BALANCED(3, "balanced", "均衡型");

        private final int code;
        private final String description;
        private final String displayName;

        JobType(int code, String description, String displayName) {
            this.code = code;
            this.description = description;
            this.displayName = displayName;
        }

        public static JobType fromCode(Integer code) {
            if (code == null) {
                return BALANCED;
            }
            for (JobType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return BALANCED;
        }

        public static JobType fromValue(String value) {
            if (value == null || value.isBlank()) {
                return BALANCED;
            }
            String normalized = value.trim().toLowerCase().replace('-', '_');
            for (JobType type : values()) {
                if (type.name().equalsIgnoreCase(normalized)
                        || type.description.equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            return BALANCED;
        }
    }

    /**
     * 岗位类型
     */
    private JobType jobType;

    /**
     * 技术基础题数量
     */
    private int technicalBasicCount;

    /**
     * 项目经验题数量
     */
    private int projectCount;

    /**
     * 业务场景题数量
     */
    private int businessCount;

    /**
     * 软技能题数量
     */
    private int softSkillCount;

    /**
     * 总题目数量
     */
    private int totalQuestions;

    /**
     * 岗位关键要求摘要（供后续节点使用）
     */
    private String keyRequirements;

    /**
     * 技术栈摘要（技术岗位专用）
     */
    private String techStackSummary;

    /**
     * 业务领域摘要
     */
    private String businessDomain;

    /**
     * 软技能要求摘要
     */
    private String softSkillsRequired;

    /**
     * 获取技术轮题目总数
     */
    public int getTechnicalRoundTotal() {
        return technicalBasicCount + projectCount;
    }

    /**
     * 获取业务轮题目总数
     */
    public int getBusinessRoundTotal() {
        return businessCount + softSkillCount;
    }

    /**
     * 生成岗位摘要文本（供其他节点使用）
     */
    public String generateJobSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Job Type: ").append(jobType.getDisplayName()).append("\n");
        if (keyRequirements != null && !keyRequirements.isEmpty()) {
            summary.append("Key Requirements: ").append(keyRequirements).append("\n");
        }
        if (techStackSummary != null && !techStackSummary.isEmpty()) {
            summary.append("Tech Stack: ").append(techStackSummary).append("\n");
        }
        if (businessDomain != null && !businessDomain.isEmpty()) {
            summary.append("Business Domain: ").append(businessDomain).append("\n");
        }
        if (softSkillsRequired != null && !softSkillsRequired.isEmpty()) {
            summary.append("Soft Skills Required: ").append(softSkillsRequired).append("\n");
        }
        return summary.toString();
    }
}
