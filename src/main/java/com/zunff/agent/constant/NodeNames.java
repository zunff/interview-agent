package com.zunff.agent.constant;

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
    public static final String GENERATE_QUESTION = "generateQuestion";
    public static final String ASK_QUESTION = "askQuestion";
    public static final String WAIT_FOR_ANSWER = "waitForAnswer";
    public static final String EVALUATE_ANSWER = "evaluateAnswer";
    public static final String FOLLOW_UP_DECISION = "followUpDecision";
    public static final String GENERATE_FOLLOW_UP = "generateFollowUp";
    public static final String NEXT_QUESTION = "nextQuestion";
    public static final String ROUND_TRANSITION = "roundTransition";
    public static final String GENERATE_REPORT = "generateReport";

    // ========== 子图节点 ==========
    public static final String TECHNICAL_ROUND = "technicalRound";
    public static final String BUSINESS_ROUND = "businessRound";

    // ========== 子图内部节点前缀 ==========
    public static final String TECH_PREFIX = "tech_";
    public static final String BIZ_PREFIX = "biz_";
}