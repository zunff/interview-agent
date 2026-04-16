package com.zunff.interview.agent.names;

/**
 * 节点名称常量
 * 用于状态图中的节点标识
 */
public interface NodeNames {

    // ========== 主图节点 ==========
    String INIT = "init";
    String JOB_ANALYSIS = "jobAnalysis";
    String SELF_INTRO = "selfIntro";
    String PROFILE_ANALYSIS = "profileAnalysis";
    String BATCH_QUESTION_GENERATOR = "batchQuestionGenerator";
    String ROUND_TRANSITION = "roundTransition";
    String GENERATE_REPORT = "generateReport";

    // ========== 子图节点 ==========
    String TECHNICAL_ROUND = "technicalRound";
    String BUSINESS_ROUND = "businessRound";

    // ========== 子图内部节点前缀 ==========
    String TECH_PREFIX = "tech_";
    String BIZ_PREFIX = "biz_";

    // ========== 子图内部节点 ==========
    String ASK_QUESTION = "askQuestion";
    String COMPREHENSIVE_EVALUATION = "comprehensiveEvaluation";
    String FOLLOW_UP_DECISION = "followUpDecision";
    String GENERATE_FOLLOW_UP = "generateFollowUp";
    String GENERATE_CHALLENGE = "generateChallenge";
    String GENERATE_DEEP_DIVE = "generateDeepDive";
}