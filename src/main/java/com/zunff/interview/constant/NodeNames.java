package com.zunff.interview.constant;

/**
 * 节点名称常量
 * 用于状态图中的节点标识
 */
public final class NodeNames {

    private NodeNames() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }

    // ========== 主图节点 ==========
    public static final String INIT = "init";
    public static final String JOB_ANALYSIS = "jobAnalysis";
    public static final String SELF_INTRO = "selfIntro";
    public static final String PROFILE_ANALYSIS = "profileAnalysis";
    public static final String GENERATE_QUESTION = "generateQuestion";
    public static final String ASK_QUESTION = "askQuestion";
    public static final String FOLLOW_UP_DECISION = "followUpDecision";
    public static final String GENERATE_FOLLOW_UP = "generateFollowUp";
    public static final String ROUND_TRANSITION = "roundTransition";
    public static final String GENERATE_REPORT = "generateReport";

    // ========== 子图节点 ==========
    public static final String TECHNICAL_ROUND = "technicalRound";
    public static final String BUSINESS_ROUND = "businessRound";

    // ========== 子图内部节点前缀 ==========
    public static final String TECH_PREFIX = "tech_";
    public static final String BIZ_PREFIX = "biz_";

    // ========== 子图内部节点 ==========
    public static final String ANALYZE_VISION = "analyzeVision";
    public static final String ANALYZE_AUDIO = "analyzeAudio";
    public static final String AGGREGATE_ANALYSIS = "aggregateAnalysis";
    public static final String COMPREHENSIVE_EVALUATION = "comprehensiveEvaluation";
    public static final String GENERATE_CHALLENGE = "generateChallenge";
    public static final String GENERATE_DEEP_DIVE = "generateDeepDive";
}