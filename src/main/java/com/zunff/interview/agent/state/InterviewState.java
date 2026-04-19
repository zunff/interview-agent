package com.zunff.interview.agent.state;

import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.InterviewQuestionBO;
import com.zunff.interview.model.bo.FollowUpChainEntity;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.bo.JobAnalysisResult;
import com.zunff.interview.model.bo.LevelMatchResult;
import com.zunff.interview.constant.Difficulty;
import com.zunff.interview.constant.DifficultyPreference;
import com.zunff.interview.model.bo.analysis.FrameWithTimestamp;
import com.zunff.interview.model.bo.analysis.TranscriptEntry;
import com.zunff.interview.model.dto.llm.resp.CandidateProfileResponseDto;
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
    public static final String MAIN_GENERATED_QUESTION = "mainGeneratedQuestion"; // 当前主问题（不包含追问）
    public static final String CURRENT_GENERATED_QUESTION = "currentGeneratedQuestion"; // 当前问题（可能是主问题或追问）

    // ========== 回答与评估 ==========
    public static final String ANSWER_TEXT = "answerText";
    public static final String ANSWER_AUDIO = "answerAudio";
    public static final String ANSWER_FRAMES = "answerFrames";
    public static final String ANSWER_FRAMES_WITH_TIMESTAMPS = "answerFramesWithTimestamps";
    public static final String CURRENT_EVALUATION = "currentEvaluation";

    // ========== 实时ASR转录结果 ==========
    public static final String TRANSCRIPT_ENTRIES = "transcriptEntries";

    // ========== 追问控制 ==========
    public static final String FOLLOW_UP_COUNT = "followUpCount";
    public static final String FOLLOW_UP_QUESTION = "followUpQuestion";
    public static final String DECISION = "decision";  // 路由决策: followUp/deepDive/challengeMode/nextQuestion
    public static final String FOLLOW_UP_CHAIN = "followUpChain"; // 追问链路：记录追问问题和详细评价

    // ========== 已评估题目列表（按时间顺序：主1 追问1 主2 追问2） ==========
    public static final String EVALUATED_TECHNICAL_QUESTIONS = "evaluatedTechnicalQuestions";
    public static final String EVALUATED_BUSINESS_QUESTIONS = "evaluatedBusinessQuestions";

    // ========== 最终报告 ==========
    public static final String IS_FINISHED = "isFinished";

    // ========== 轮次管理 ==========
    public static final String CURRENT_ROUND = "currentRound";           // TECHNICAL / BUSINESS

    // ========== 提前结束检测 ==========
    public static final String CONSECUTIVE_HIGH_SCORES = "consecutiveHighScores";

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
    public static final String KNOWLEDGE_COMPANY = "knowledgeCompany";
    public static final String KNOWLEDGE_JOB_POSITION = "knowledgeJobPosition";

    // ========== 岗位级别 ==========
    public static final String POSITION_LEVEL = "positionLevel";
    public static final String LEVEL_MATCH_RESULT = "levelMatchResult";

    // ========== 批量题目队列 ==========
    public static final String TECHNICAL_QUESTIONS_QUEUE = "technicalQuestionsQueue";  // List<GeneratedQuestion>
    public static final String BUSINESS_QUESTIONS_QUEUE = "businessQuestionsQueue";    // List<GeneratedQuestion>

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
        // ANSWER_FRAMES 使用 base（覆盖语义），因为每次回答的视频帧是独立的
        SCHEMA.put(ANSWER_FRAMES, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(ANSWER_FRAMES_WITH_TIMESTAMPS, Channels.base(new LastValueReducer<>(), ArrayList::new));

        // 实时ASR转录结果（每次回答替换，不累积）
        SCHEMA.put(TRANSCRIPT_ENTRIES, Channels.base(new LastValueReducer<>(), ArrayList::new));

        // 最后值类型：使用 base + LastValueReducer
        SCHEMA.put(MAIN_GENERATED_QUESTION, Channels.base(new LastValueReducer<>(), GeneratedQuestion::new));
        SCHEMA.put(CURRENT_GENERATED_QUESTION, Channels.base(new LastValueReducer<>(), GeneratedQuestion::new));
        SCHEMA.put(ANSWER_TEXT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(ANSWER_AUDIO, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(CURRENT_EVALUATION, Channels.base(new LastValueReducer<>(), EvaluationBO::new));
        SCHEMA.put(FOLLOW_UP_COUNT, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(FOLLOW_UP_QUESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(DECISION, Channels.base(new LastValueReducer<>(), () -> "nextQuestion"));
        SCHEMA.put(FOLLOW_UP_CHAIN, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(EVALUATED_TECHNICAL_QUESTIONS, Channels.appender(ArrayList::new));
        SCHEMA.put(EVALUATED_BUSINESS_QUESTIONS, Channels.appender(ArrayList::new));
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
        SCHEMA.put(CONSECUTIVE_HIGH_SCORES, Channels.base(new LastValueReducer<>(), () -> 0));

        // 岗位分析
        SCHEMA.put(JOB_ANALYSIS_RESULT, Channels.base(new LastValueReducer<>(), JobAnalysisResult::new));
        SCHEMA.put(KNOWLEDGE_COMPANY, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(KNOWLEDGE_JOB_POSITION, Channels.base(new LastValueReducer<>(), () -> ""));

        // 岗位级别
        SCHEMA.put(POSITION_LEVEL, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(LEVEL_MATCH_RESULT, Channels.base(new LastValueReducer<>(),
                () -> new LevelMatchResult(
                        JobAnalysisResult.PositionLevel.MID_LEVEL,
                        JobAnalysisResult.PositionLevel.MID_LEVEL,
                        Difficulty.EASY,
                        Difficulty.HARD,
                        DifficultyPreference.STANDARD
                )));

        // 批量题目队列
        SCHEMA.put(TECHNICAL_QUESTIONS_QUEUE, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(BUSINESS_QUESTIONS_QUEUE, Channels.base(new LastValueReducer<>(), ArrayList::new));

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

    /**
     * 获取候选人画像对象
     */
    public CandidateProfileResponseDto candidateProfile() {
        Object value = data().get(CANDIDATE_PROFILE);
        return value instanceof CandidateProfileResponseDto ? (CandidateProfileResponseDto) value : null;
    }

    /**
     * 获取候选人画像文本描述（用于 prompt，英文格式）
     */
    public String candidateProfileAsText() {
        CandidateProfileResponseDto profile = candidateProfile();
        if (profile == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (profile.techStack() != null && !profile.techStack().isEmpty()) {
            sb.append("Tech Stack: ").append(String.join(", ", profile.techStack())).append("\n");
        }

        if (profile.keyProjects() != null && !profile.keyProjects().isEmpty()) {
            sb.append("\nKey Projects:\n");
            for (int i = 0; i < profile.keyProjects().size(); i++) {
                var project = profile.keyProjects().get(i);
                sb.append(i + 1).append(". ").append(project.name());
                if (project.role() != null && !project.role().isEmpty()) {
                    sb.append(" (Role: ").append(project.role()).append(")");
                }
                if (project.highlights() != null && !project.highlights().isEmpty()) {
                    sb.append("\n   Highlights: ").append(String.join("; ", project.highlights()));
                }
                sb.append("\n");
            }
        }

        if (profile.workYears() != null) {
            sb.append("\nWork Experience: ").append(profile.workYears()).append(" years\n");
        }

        if (profile.education() != null && !profile.education().isEmpty()) {
            sb.append("Education: ").append(profile.education()).append("\n");
        }

        if (profile.summary() != null && !profile.summary().isEmpty()) {
            sb.append("\nSummary: ").append(profile.summary()).append("\n");
        }

        if (profile.highlights() != null && !profile.highlights().isEmpty()) {
            sb.append("\nHighlights:\n- ").append(String.join("\n- ", profile.highlights())).append("\n");
        }

        if (profile.concerns() != null && !profile.concerns().isEmpty()) {
            sb.append("\nPotential Concerns:\n- ").append(String.join("\n- ", profile.concerns())).append("\n");
        }

        if (profile.impressionScore() != null) {
            sb.append("\nInitial Impression Score: ").append(profile.impressionScore()).append("/100\n");
        }

        if (profile.selfIntroConsistency() != null && !profile.selfIntroConsistency().isEmpty()) {
            sb.append("Self-Introduction Consistency: ").append(profile.selfIntroConsistency()).append("\n");
        }

        return sb.toString().trim();
    }

    public String selfIntro() {
        return (String) data().getOrDefault(SELF_INTRO, "");
    }

    public String currentQuestion() {
        GeneratedQuestion q = getCurrentGeneratedQuestion();
        return q != null ? q.getQuestion() : "";
    }

    public String questionType() {
        GeneratedQuestion q = getCurrentGeneratedQuestion();
        return q != null && q.getQuestionType() != null ? q.getQuestionType() : QuestionType.TECHNICAL_BASIC.getDisplayName();
    }

    /**
     * 获取当前问题索引
     */
    public int questionIndex() {
        GeneratedQuestion q = getCurrentGeneratedQuestion();
        return q != null ? q.getQuestionIndex() : 0;
    }

    /**
     * 获取当前题目的完整 GeneratedQuestion 对象
     */
    public GeneratedQuestion getCurrentGeneratedQuestion() {
        return (GeneratedQuestion) data().get(CURRENT_GENERATED_QUESTION);
    }

    /**
     * 获取当前主问题的完整 GeneratedQuestion 对象（不包含追问）
     */
    public GeneratedQuestion getMainGeneratedQuestion() {
        return (GeneratedQuestion) data().get(MAIN_GENERATED_QUESTION);
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
        return (String) data().getOrDefault(DECISION, RouteDecision.NEXT_QUESTION.getValue());
    }

    public boolean isFinished() {
        Object value = data().get(IS_FINISHED);
        return Boolean.TRUE.equals(value);
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
     * 获取技术轮已问问题数（仅主问题）
     */
    public int technicalQuestionsDone() {
        return (int) evaluatedTechnicalQuestions().stream()
                .filter(q -> !q.isFollowUp())
                .count();
    }

    /**
     * 获取业务轮已问问题数（仅主问题）
     */
    public int businessQuestionsDone() {
        return (int) evaluatedBusinessQuestions().stream()
                .filter(q -> !q.isFollowUp())
                .count();
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
     * 获取当前轮次的追问上限
     */
    public int maxFollowUpsForCurrentRound() {
        String key = isTechnicalRound() ? MAX_FOLLOW_UPS_TECHNICAL : MAX_FOLLOW_UPS_BUSINESS;
        Object value = data().get(key);
        return value instanceof Number ? ((Number) value).intValue() : 2;
    }

    /**
     * 技术轮是否完成
     */
    public boolean isTechnicalRoundComplete() {
        return technicalQuestionsQueue().isEmpty();
    }

    /**
     * 业务轮是否完成
     */
    public boolean isBusinessRoundComplete() {
        return businessQuestionsQueue().isEmpty();
    }

    // ========== 岗位分析便捷方法 ==========

    /**
     * 获取岗位分析结果
     */
    public JobAnalysisResult jobAnalysisResult() {
        return (JobAnalysisResult) data().get(JOB_ANALYSIS_RESULT);
    }

    /**
     * 是否有岗位分析结果
     */
    public boolean hasJobAnalysisResult() {
        return data().containsKey(JOB_ANALYSIS_RESULT) && data().get(JOB_ANALYSIS_RESULT) != null;
    }

    public String knowledgeCompany() {
        return (String) data().getOrDefault(KNOWLEDGE_COMPANY, "");
    }

    public String knowledgeJobPosition() {
        return (String) data().getOrDefault(KNOWLEDGE_JOB_POSITION, "");
    }

    /**
     * 获取前端传入的岗位级别
     */
    public String positionLevel() {
        return (String) data().getOrDefault(POSITION_LEVEL, "");
    }

    /**
     * 获取级别匹配结果
     */
    public LevelMatchResult levelMatchResult() {
        return (LevelMatchResult) data().get(LEVEL_MATCH_RESULT);
    }

    /**
     * 是否有级别匹配结果
     */
    public boolean hasLevelMatchResult() {
        return data().containsKey(LEVEL_MATCH_RESULT) && data().get(LEVEL_MATCH_RESULT) != null;
    }

    // ========== 批量题目队列便捷方法 ==========

    /**
     * 获取技术轮题目队列
     */
    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> technicalQuestionsQueue() {
        return (List<GeneratedQuestion>) data().getOrDefault(TECHNICAL_QUESTIONS_QUEUE, new ArrayList<>());
    }

    /**
     * 获取业务轮题目队列
     */
    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> businessQuestionsQueue() {
        return (List<GeneratedQuestion>) data().getOrDefault(BUSINESS_QUESTIONS_QUEUE, new ArrayList<>());
    }

    /**
     * 获取已评估的技术轮题目列表（按时间顺序）
     */
    @SuppressWarnings("unchecked")
    public List<InterviewQuestionBO> evaluatedTechnicalQuestions() {
        return (List<InterviewQuestionBO>) data().getOrDefault(EVALUATED_TECHNICAL_QUESTIONS, new ArrayList<>());
    }

    /**
     * 获取已评估的业务轮题目列表（按时间顺序）
     */
    @SuppressWarnings("unchecked")
    public List<InterviewQuestionBO> evaluatedBusinessQuestions() {
        return (List<InterviewQuestionBO>) data().getOrDefault(EVALUATED_BUSINESS_QUESTIONS, new ArrayList<>());
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

    /**
     * 获取追问链路
     */
    @SuppressWarnings("unchecked")
    public List<FollowUpChainEntity> followUpChain() {
        return (List<FollowUpChainEntity>) data().getOrDefault(FOLLOW_UP_CHAIN, new ArrayList<>());
    }

    /**
     * 格式化追问链路用于提示词（英文格式）
     */
    public String formatFollowUpChain() {
        List<FollowUpChainEntity> chain = followUpChain();
        if (chain == null || chain.isEmpty()) {
            return "No follow-up history";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            FollowUpChainEntity entry = chain.get(i);
            sb.append(String.format("%n### Follow-up %d%n", i + 1));
            sb.append(String.format("**Question**: %s%n", entry.getFollowUpQuestion()));
            sb.append(String.format("**Score**: %d%n", entry.getOverallScore()));
            sb.append(String.format("**Evaluation**: %s%n", entry.getDetailedEvaluation()));
        }
        return sb.toString();
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
