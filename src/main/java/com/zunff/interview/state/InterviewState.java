package com.zunff.interview.state;

import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.JobAnalysisResult;
import com.zunff.interview.model.dto.analysis.FrameWithTimestamp;
import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试状态定义
 * 用于 LangGraph4j 状态图中传递和管理面试过程中的所有数据
 */
@Getter
public class InterviewState extends AgentState {

    // ========== 面试上下文 ==========
    public static final String RESUME = "resume";
    public static final String JOB_INFO = "jobInfo";
    public static final String SESSION_ID = "sessionId";
    public static final String CANDIDATE_PROFILE = "candidateProfile";
    public static final String SELF_INTRO = "selfIntro";

    // ========== 问题管理 ==========
    public static final String QUESTIONS = "questions";
    public static final String CURRENT_QUESTION = "currentQuestion";
    public static final String QUESTION_INDEX = "questionIndex";
    public static final String QUESTION_TYPE = "questionType";

    // ========== 回答与评估 ==========
    public static final String ANSWER_TEXT = "answerText";
    public static final String ANSWER_AUDIO = "answerAudio";
    public static final String ANSWER_FRAMES = "answerFrames";
    public static final String ANSWER_FRAMES_WITH_TIMESTAMPS = "answerFramesWithTimestamps";
    public static final String CURRENT_EVALUATION = "currentEvaluation";
    public static final String EVALUATIONS = "evaluations";

    // ========== 实时ASR转录结果 ==========
    public static final String TRANSCRIPT_ENTRIES = "transcriptEntries";

    // ========== 实时多模态分析 ==========
    public static final String EMOTION_SCORES = "emotionScores";
    public static final String BODY_LANGUAGE_SCORES = "bodyLanguageScores";
    public static final String VOICE_TONE_SCORES = "voiceToneScores";

    // ========== 追问控制 ==========
    public static final String FOLLOW_UP_COUNT = "followUpCount";
    public static final String FOLLOW_UP_QUESTION = "followUpQuestion";
    public static final String DECISION = "decision";  // 路由决策: followUp/deepDive/challengeMode/nextQuestion

    // ========== 最终报告 ==========
    public static final String FINAL_REPORT = "finalReport";
    public static final String IS_FINISHED = "isFinished";

    // ========== 轮次管理 ==========
    public static final String CURRENT_ROUND = "currentRound";           // TECHNICAL / BUSINESS
    public static final String TECHNICAL_QUESTIONS_DONE = "technicalQuestionsDone";
    public static final String BUSINESS_QUESTIONS_DONE = "businessQuestionsDone";
    public static final String TECHNICAL_ROUND_SCORES = "technicalRoundScores";  // List<Integer>
    public static final String BUSINESS_ROUND_SCORES = "businessRoundScores";    // List<Integer>

    // ========== 提前结束检测 ==========
    public static final String CONSECUTIVE_HIGH_SCORES = "consecutiveHighScores";

    // ========== 多模态建议 ==========
    public static final String MODALITY_FOLLOW_UP_SUGGESTION = "modalityFollowUpSuggestion";
    public static final String MODALITY_CONCERN = "modalityConcern";

    // ========== 配置参数 ==========
    public static final String MAX_TECHNICAL_QUESTIONS = "maxTechnicalQuestions";  // 默认6
    public static final String MAX_BUSINESS_QUESTIONS = "maxBusinessQuestions";    // 默认4
    public static final String ROUND_PASS_SCORE = "roundPassScore";                // 默认75
    public static final String HIGH_SCORE_THRESHOLD = "highScoreThreshold";        // 默认85
    public static final String CONSECUTIVE_HIGH_FOR_EARLY_END = "consecutiveHighForEarlyEnd"; // 默认3
    public static final String MAX_FOLLOW_UPS_TECHNICAL = "maxFollowUpsTechnical"; // 默认3
    public static final String MAX_FOLLOW_UPS_BUSINESS = "maxFollowUpsBusiness";   // 默认2

    // ========== 岗位分析 ==========
    public static final String JOB_ANALYSIS_RESULT = "jobAnalysisResult";          // JobAnalysisResult 对象
    public static final String CURRENT_QUESTION_CATEGORY = "currentQuestionCategory"; // 当前题目类别索引

    // ========== 熔断机制 ==========
    public static final String CONSECUTIVE_LLM_FAILURES = "consecutiveLLMFailures";
    public static final String MAX_LLM_FAILURES = "maxLLMFailures";                // 默认3

    /**
     * LastValue Reducer: 新值覆盖旧值
     */
    private static class LastValueReducer<T> implements Reducer<T> {
        @Override
        public T apply(T currentValue, T newValue) {
            return newValue;
        }
    }

    /**
     * 状态 Schema 定义
     */
    public static final Map<String, Channel<?>> SCHEMA = new HashMap<>();

    static {
        // 列表类型：使用 appender 累加
        SCHEMA.put(QUESTIONS, Channels.appender(ArrayList::new));
        SCHEMA.put(EVALUATIONS, Channels.appender(ArrayList::new));
        SCHEMA.put(EMOTION_SCORES, Channels.appender(ArrayList::new));
        SCHEMA.put(BODY_LANGUAGE_SCORES, Channels.appender(ArrayList::new));
        SCHEMA.put(VOICE_TONE_SCORES, Channels.appender(ArrayList::new));
        // ANSWER_FRAMES 使用 base（覆盖语义），因为每次回答的视频帧是独立的
        SCHEMA.put(ANSWER_FRAMES, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(ANSWER_FRAMES_WITH_TIMESTAMPS, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(TECHNICAL_ROUND_SCORES, Channels.appender(ArrayList::new));
        SCHEMA.put(BUSINESS_ROUND_SCORES, Channels.appender(ArrayList::new));

        // 实时ASR转录结果
        SCHEMA.put(TRANSCRIPT_ENTRIES, Channels.appender(ArrayList::new));

        // 最后值类型：使用 base + LastValueReducer
        SCHEMA.put(QUESTION_INDEX, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(CURRENT_QUESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(QUESTION_TYPE, Channels.base(new LastValueReducer<>(), () -> QuestionType.TECHNICAL_BASIC.getDisplayName()));
        SCHEMA.put(ANSWER_TEXT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(ANSWER_AUDIO, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(CURRENT_EVALUATION, Channels.base(new LastValueReducer<>(), EvaluationBO::new));
        SCHEMA.put(FOLLOW_UP_COUNT, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(FOLLOW_UP_QUESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(DECISION, Channels.base(new LastValueReducer<>(), () -> "nextQuestion"));
        SCHEMA.put(FINAL_REPORT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(IS_FINISHED, Channels.base(new LastValueReducer<>(), () -> false));
        SCHEMA.put(MAX_TECHNICAL_QUESTIONS, Channels.base(new LastValueReducer<>(), () -> 6));
        SCHEMA.put(MAX_BUSINESS_QUESTIONS, Channels.base(new LastValueReducer<>(), () -> 4));
        SCHEMA.put(ROUND_PASS_SCORE, Channels.base(new LastValueReducer<>(), () -> 75));
        SCHEMA.put(HIGH_SCORE_THRESHOLD, Channels.base(new LastValueReducer<>(), () -> 85));
        SCHEMA.put(CONSECUTIVE_HIGH_FOR_EARLY_END, Channels.base(new LastValueReducer<>(), () -> 3));
        SCHEMA.put(MAX_FOLLOW_UPS_TECHNICAL, Channels.base(new LastValueReducer<>(), () -> 3));
        SCHEMA.put(MAX_FOLLOW_UPS_BUSINESS, Channels.base(new LastValueReducer<>(), () -> 2));

        // 轮次管理
        SCHEMA.put(CURRENT_ROUND, Channels.base(new LastValueReducer<>(), () -> InterviewRound.TECHNICAL.getCode()));
        SCHEMA.put(TECHNICAL_QUESTIONS_DONE, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(BUSINESS_QUESTIONS_DONE, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(CONSECUTIVE_HIGH_SCORES, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(MODALITY_FOLLOW_UP_SUGGESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(MODALITY_CONCERN, Channels.base(new LastValueReducer<>(), () -> false));

        // 岗位分析
        SCHEMA.put(JOB_ANALYSIS_RESULT, Channels.base(new LastValueReducer<>(), JobAnalysisResult::new));
        SCHEMA.put(CURRENT_QUESTION_CATEGORY, Channels.base(new LastValueReducer<>(), () -> 0));

        // 熔断机制
        SCHEMA.put(CONSECUTIVE_LLM_FAILURES, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(MAX_LLM_FAILURES, Channels.base(new LastValueReducer<>(), () -> 3));

        // 上下文信息也使用 base
        SCHEMA.put(RESUME, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(JOB_INFO, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(SESSION_ID, Channels.base(new LastValueReducer<>(), () -> ""));

        // 候选人画像（简历 + 自我介绍综合分析）
        SCHEMA.put(CANDIDATE_PROFILE, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(SELF_INTRO, Channels.base(new LastValueReducer<>(), () -> ""));
    }

    public InterviewState(Map<String, Object> initData) {
        super(initData);
    }

    // ========== 便捷方法 ==========

    public String resume() {
        return (String) data().getOrDefault(RESUME, "");
    }

    public String jobInfo() {
        return (String) data().getOrDefault(JOB_INFO, "");
    }

    public String sessionId() {
        return (String) data().getOrDefault(SESSION_ID, "");
    }

    public String candidateProfile() {
        return (String) data().getOrDefault(CANDIDATE_PROFILE, "");
    }

    public String selfIntro() {
        return (String) data().getOrDefault(SELF_INTRO, "");
    }

    public int questionIndex() {
        Object value = data().get(QUESTION_INDEX);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public String currentQuestion() {
        return (String) data().get(CURRENT_QUESTION);
    }

    public String questionType() {
        return (String) data().getOrDefault(QUESTION_TYPE, QuestionType.TECHNICAL_BASIC.getDisplayName());
    }

    public String answerText() {
        return (String) data().getOrDefault(ANSWER_TEXT, "");
    }

    /**
     * 获取当前回答的视频帧列表
     */
    @SuppressWarnings("unchecked")
    public List<String> answerFrames() {
        return (List<String>) data().getOrDefault(ANSWER_FRAMES, new ArrayList<>());
    }

    /**
     * 获取带时间戳的视频帧列表
     */
    @SuppressWarnings("unchecked")
    public List<FrameWithTimestamp> answerFramesWithTimestamps() {
        return (List<FrameWithTimestamp>) data().getOrDefault(ANSWER_FRAMES_WITH_TIMESTAMPS, new ArrayList<>());
    }

    public int followUpCount() {
        Object value = data().get(FOLLOW_UP_COUNT);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 获取路由决策
     */
    public String decision() {
        return (String) data().getOrDefault(DECISION, "nextQuestion");
    }

    public boolean isFinished() {
        Object value = data().get(IS_FINISHED);
        return Boolean.TRUE.equals(value);
    }

    @SuppressWarnings("unchecked")
    public List<String> questions() {
        return (List<String>) data().getOrDefault(QUESTIONS, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> evaluations() {
        return (List<Map<String, Object>>) data().getOrDefault(EVALUATIONS, new ArrayList<>());
    }

    public String getFinalReport() {
        return (String) data().getOrDefault(FINAL_REPORT, "");
    }

    // ========== 轮次管理便捷方法 ==========

    /**
     * 获取当前轮次
     */
    public String currentRound() {
        return (String) data().getOrDefault(CURRENT_ROUND, InterviewRound.TECHNICAL.getCode());
    }

    /**
     * 获取当前轮次枚举
     */
    public InterviewRound currentRoundEnum() {
        return InterviewRound.fromCode(currentRound());
    }

    /**
     * 是否为技术轮
     */
    public boolean isTechnicalRound() {
        return currentRoundEnum().isTechnical();
    }

    /**
     * 是否为业务轮
     */
    public boolean isBusinessRound() {
        return currentRoundEnum().isBusiness();
    }

    /**
     * 获取技术轮已问问题数
     */
    public int technicalQuestionsDone() {
        Object value = data().get(TECHNICAL_QUESTIONS_DONE);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 获取业务轮已问问题数
     */
    public int businessQuestionsDone() {
        Object value = data().get(BUSINESS_QUESTIONS_DONE);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 获取技术轮最大问题数
     */
    public int maxTechnicalQuestions() {
        Object value = data().get(MAX_TECHNICAL_QUESTIONS);
        return value instanceof Number ? ((Number) value).intValue() : 6;
    }

    /**
     * 获取业务轮最大问题数
     */
    public int maxBusinessQuestions() {
        Object value = data().get(MAX_BUSINESS_QUESTIONS);
        return value instanceof Number ? ((Number) value).intValue() : 4;
    }

    /**
     * 获取轮次通过分数
     */
    public int roundPassScore() {
        Object value = data().get(ROUND_PASS_SCORE);
        return value instanceof Number ? ((Number) value).intValue() : 75;
    }

    /**
     * 获取高分阈值
     */
    public int highScoreThreshold() {
        Object value = data().get(HIGH_SCORE_THRESHOLD);
        return value instanceof Number ? ((Number) value).intValue() : 85;
    }

    /**
     * 获取连续高分次数要求
     */
    public int consecutiveHighForEarlyEnd() {
        Object value = data().get(CONSECUTIVE_HIGH_FOR_EARLY_END);
        return value instanceof Number ? ((Number) value).intValue() : 3;
    }

    /**
     * 获取当前连续高分次数
     */
    public int consecutiveHighScores() {
        Object value = data().get(CONSECUTIVE_HIGH_SCORES);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 获取当前轮次的追问上限
     */
    public int maxFollowUpsForCurrentRound() {
        String key = isTechnicalRound() ? MAX_FOLLOW_UPS_TECHNICAL : MAX_FOLLOW_UPS_BUSINESS;
        Object value = data().get(key);
        return value instanceof Number ? ((Number) value).intValue() : 2;
    }

    /**
     * 获取技术轮分数列表
     */
    @SuppressWarnings("unchecked")
    public List<Integer> technicalRoundScores() {
        return (List<Integer>) data().getOrDefault(TECHNICAL_ROUND_SCORES, new ArrayList<>());
    }

    /**
     * 获取业务轮分数列表
     */
    @SuppressWarnings("unchecked")
    public List<Integer> businessRoundScores() {
        return (List<Integer>) data().getOrDefault(BUSINESS_ROUND_SCORES, new ArrayList<>());
    }

    /**
     * 计算技术轮平均分
     */
    public double technicalAverageScore() {
        List<Integer> scores = technicalRoundScores();
        if (scores.isEmpty()) return 0;
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /**
     * 计算业务轮平均分
     */
    public double businessAverageScore() {
        List<Integer> scores = businessRoundScores();
        if (scores.isEmpty()) return 0;
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /**
     * 是否可以提前结束面试（连续高分）
     */
    public boolean canEndInterviewEarly() {
        return consecutiveHighScores() >= consecutiveHighForEarlyEnd();
    }

    /**
     * 技术轮是否完成
     */
    public boolean isTechnicalRoundComplete() {
        return technicalQuestionsDone() >= maxTechnicalQuestions();
    }

    /**
     * 业务轮是否完成
     */
    public boolean isBusinessRoundComplete() {
        return businessQuestionsDone() >= maxBusinessQuestions();
    }

    /**
     * 获取多模态追问建议
     */
    public String modalityFollowUpSuggestion() {
        return (String) data().getOrDefault(MODALITY_FOLLOW_UP_SUGGESTION, "");
    }

    /**
     * 是否存在多模态异常
     */
    public boolean modalityConcern() {
        Object value = data().get(MODALITY_CONCERN);
        return Boolean.TRUE.equals(value);
    }

    // ========== 岗位分析便捷方法 ==========

    /**
     * 获取岗位分析结果
     */
    @SuppressWarnings("unchecked")
    public com.zunff.interview.model.dto.JobAnalysisResult jobAnalysisResult() {
        return (com.zunff.interview.model.dto.JobAnalysisResult) data().get(JOB_ANALYSIS_RESULT);
    }

    /**
     * 获取当前题目类别索引
     */
    public int currentQuestionCategory() {
        Object value = data().get(CURRENT_QUESTION_CATEGORY);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 是否有岗位分析结果
     */
    public boolean hasJobAnalysisResult() {
        return data().containsKey(JOB_ANALYSIS_RESULT) && data().get(JOB_ANALYSIS_RESULT) != null;
    }

    // ========== 多模态分析中间结果便捷方法 ==========


    /**
     * 获取转录条目列表
     */
    @SuppressWarnings("unchecked")
    public List<TranscriptEntry> transcriptEntries() {
        return (List<TranscriptEntry>) data().getOrDefault(TRANSCRIPT_ENTRIES, new ArrayList<>());
    }

    /**
     * 获取当前评估结果
     */
    public EvaluationBO getCurrentEvaluation() {
        return (EvaluationBO) data().getOrDefault(CURRENT_EVALUATION, new EvaluationBO());
    }

    // ========== 熔断机制便捷方法 ==========

    /**
     * 获取连续 LLM 失败次数
     */
    public int consecutiveLLMFailures() {
        Object value = data().get(CONSECUTIVE_LLM_FAILURES);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /**
     * 获取最大允许 LLM 失败次数
     */
    public int maxLLMFailures() {
        Object value = data().get(MAX_LLM_FAILURES);
        return value instanceof Number ? ((Number) value).intValue() : 3;
    }

    /**
     * 是否触发 LLM 熔断
     */
    public boolean isLLMCircuitBroken() {
        return consecutiveLLMFailures() >= maxLLMFailures();
    }
}
