package com.zunff.agent.state;

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
    public static final String INTERVIEW_TYPE = "interviewType";
    public static final String SESSION_ID = "sessionId";

    // ========== 问题管理 ==========
    public static final String QUESTIONS = "questions";
    public static final String CURRENT_QUESTION = "currentQuestion";
    public static final String QUESTION_INDEX = "questionIndex";
    public static final String QUESTION_TYPE = "questionType";

    // ========== 回答与评估 ==========
    public static final String ANSWER_TEXT = "answerText";
    public static final String ANSWER_AUDIO = "answerAudio";
    public static final String ANSWER_FRAMES = "answerFrames";
    public static final String CURRENT_EVALUATION = "currentEvaluation";
    public static final String EVALUATIONS = "evaluations";

    // ========== 实时多模态分析 ==========
    public static final String EMOTION_SCORES = "emotionScores";
    public static final String BODY_LANGUAGE_SCORES = "bodyLanguageScores";
    public static final String VOICE_TONE_SCORES = "voiceToneScores";

    // ========== 追问控制 ==========
    public static final String FOLLOW_UP_COUNT = "followUpCount";
    public static final String NEED_FOLLOW_UP = "needFollowUp";
    public static final String FOLLOW_UP_QUESTION = "followUpQuestion";

    // ========== 最终报告 ==========
    public static final String FINAL_REPORT = "finalReport";
    public static final String IS_FINISHED = "isFinished";

    // ========== 配置参数 ==========
    public static final String MAX_QUESTIONS = "maxQuestions";
    public static final String MAX_FOLLOW_UPS = "maxFollowUps";

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
        SCHEMA.put(ANSWER_FRAMES, Channels.appender(ArrayList::new));

        // 最后值类型：使用 base + LastValueReducer
        SCHEMA.put(QUESTION_INDEX, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(CURRENT_QUESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(QUESTION_TYPE, Channels.base(new LastValueReducer<>(), () -> "技术基础"));
        SCHEMA.put(ANSWER_TEXT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(ANSWER_AUDIO, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(CURRENT_EVALUATION, Channels.base(new LastValueReducer<>(), () -> null));
        SCHEMA.put(FOLLOW_UP_COUNT, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(NEED_FOLLOW_UP, Channels.base(new LastValueReducer<>(), () -> false));
        SCHEMA.put(FOLLOW_UP_QUESTION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(FINAL_REPORT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(IS_FINISHED, Channels.base(new LastValueReducer<>(), () -> false));
        SCHEMA.put(MAX_QUESTIONS, Channels.base(new LastValueReducer<>(), () -> 10));
        SCHEMA.put(MAX_FOLLOW_UPS, Channels.base(new LastValueReducer<>(), () -> 2));

        // 上下文信息也使用 base
        SCHEMA.put(RESUME, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(JOB_INFO, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(INTERVIEW_TYPE, Channels.base(new LastValueReducer<>(), () -> "一面"));
        SCHEMA.put(SESSION_ID, Channels.base(new LastValueReducer<>(), () -> ""));
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

    public String interviewType() {
        return (String) data().getOrDefault(INTERVIEW_TYPE, "一面");
    }

    public String sessionId() {
        return (String) data().getOrDefault(SESSION_ID, "");
    }

    public int questionIndex() {
        Object value = data().get(QUESTION_INDEX);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public String currentQuestion() {
        return (String) data().get(CURRENT_QUESTION);
    }

    public String questionType() {
        return (String) data().getOrDefault(QUESTION_TYPE, "技术基础");
    }

    public String answerText() {
        return (String) data().getOrDefault(ANSWER_TEXT, "");
    }

    public int followUpCount() {
        Object value = data().get(FOLLOW_UP_COUNT);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public boolean needFollowUp() {
        Object value = data().get(NEED_FOLLOW_UP);
        return Boolean.TRUE.equals(value);
    }

    public int maxQuestions() {
        Object value = data().get(MAX_QUESTIONS);
        return value instanceof Number ? ((Number) value).intValue() : 10;
    }

    public int maxFollowUps() {
        Object value = data().get(MAX_FOLLOW_UPS);
        return value instanceof Number ? ((Number) value).intValue() : 2;
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
}
