package com.zunff.interview.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    public enum JobType {
        /**
         * 技术驱动型：技术要求高，技术问题占比高
         */
        TECHNICAL_DRIVEN("技术驱动型"),

        /**
         * 业务驱动型：业务要求高，业务问题占比高
         */
        BUSINESS_DRIVEN("业务驱动型"),

        /**
         * 均衡型：技术与业务要求相当
         */
        BALANCED("均衡型");

        private final String displayName;

        JobType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 岗位类型
     */
    private JobType jobType;

    /**
     * 岗位类型描述
     */
    private String jobTypeDescription;

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
     * 分析依据
     */
    private String analysisReason;

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
}
