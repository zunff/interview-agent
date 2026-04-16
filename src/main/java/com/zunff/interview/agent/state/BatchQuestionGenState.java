package com.zunff.interview.agent.state;

import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.dto.GeneratedQuestion;
import lombok.Getter;
import lombok.Setter;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量题目生成子图状态
 * 独立的状态类，与主图隔离
 */
@Getter
@Setter
public class BatchQuestionGenState extends AgentState {

    // ========== 输入参数常量 ==========
    public static final String CANDIDATE_PROFILE = "candidateProfile";
    public static final String JOB_CONTEXT = "jobContext";
    public static final String SESSION_ID = "sessionId";
    public static final String KNOWLEDGE_COMPANY = "knowledgeCompany";
    public static final String KNOWLEDGE_JOB_POSITION = "knowledgeJobPosition";
    public static final String TECHNICAL_BASIC_COUNT = "technicalBasicCount";
    public static final String PROJECT_COUNT = "projectCount";
    public static final String BUSINESS_COUNT = "businessCount";
    public static final String SOFT_SKILL_COUNT = "softSkillCount";

    // ========== 中间结果常量 ==========
    public static final String TECHNICAL_BASIC_QUESTIONS = "technicalBasicQuestions";
    public static final String PROJECT_QUESTIONS = "projectQuestions";
    public static final String BUSINESS_QUESTIONS = "businessQuestions";
    public static final String SOFT_SKILL_QUESTIONS = "softSkillQuestions";

    // ========== 输出字段常量 ==========
    public static final String TECHNICAL_QUESTIONS_QUEUE = "technicalQuestionsQueue";
    public static final String BUSINESS_QUESTIONS_QUEUE = "businessQuestionsQueue";
    public static final String CURRENT_TECHNICAL_INDEX = "currentTechnicalIndex";
    public static final String CURRENT_BUSINESS_INDEX = "currentBusinessIndex";
    public static final String FALLBACK = "fallback";

    // ========== 题型配置（节点运行时参数） ==========
    public static final String QUESTION_TYPE = "questionType";
    public static final String QUESTION_COUNT = "questionCount";
    public static final String OUTPUT_KEY = "outputKey";

    /**
     * LastValue Reducer
     */
    private static class LastValueReducer<T> implements Reducer<T> {
        @Override
        public T apply(T currentValue, T newValue) {
            return newValue;
        }
    }

    /**
     * Schema 定义
     */
    public static final Map<String, Channel<?>> SCHEMA = new HashMap<>();

    static {
        SCHEMA.put(CANDIDATE_PROFILE, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(JOB_CONTEXT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(SESSION_ID, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(KNOWLEDGE_COMPANY, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(KNOWLEDGE_JOB_POSITION, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(TECHNICAL_BASIC_COUNT, Channels.base(new LastValueReducer<>(), () -> 3));
        SCHEMA.put(PROJECT_COUNT, Channels.base(new LastValueReducer<>(), () -> 3));
        SCHEMA.put(BUSINESS_COUNT, Channels.base(new LastValueReducer<>(), () -> 2));
        SCHEMA.put(SOFT_SKILL_COUNT, Channels.base(new LastValueReducer<>(), () -> 2));

        SCHEMA.put(TECHNICAL_BASIC_QUESTIONS, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(PROJECT_QUESTIONS, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(BUSINESS_QUESTIONS, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(SOFT_SKILL_QUESTIONS, Channels.base(new LastValueReducer<>(), ArrayList::new));

        SCHEMA.put(TECHNICAL_QUESTIONS_QUEUE, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(BUSINESS_QUESTIONS_QUEUE, Channels.base(new LastValueReducer<>(), ArrayList::new));
        SCHEMA.put(CURRENT_TECHNICAL_INDEX, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(CURRENT_BUSINESS_INDEX, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(FALLBACK, Channels.base(new LastValueReducer<>(), () -> false));

        SCHEMA.put(QUESTION_TYPE, Channels.base(new LastValueReducer<>(), () -> null));
        SCHEMA.put(QUESTION_COUNT, Channels.base(new LastValueReducer<>(), () -> 0));
        SCHEMA.put(OUTPUT_KEY, Channels.base(new LastValueReducer<>(), () -> ""));
    }

    /**
     * 构造函数
     */
    public BatchQuestionGenState(Map<String, Object> initData) {
        super(initData);
    }

    public BatchQuestionGenState(BatchQuestionGenState state, int count, QuestionType questionType, String outputKey) {
        super(state.data());
        data().put(QUESTION_TYPE, questionType);
        data().put(QUESTION_COUNT, count);
        data().put(OUTPUT_KEY, outputKey);
    }

    // ========== 便捷方法（兼容 QuestionTypeBatchNode） ==========

    /**
     * 兼容方法：candidateProfile()
     */
    public String candidateProfile() {
        return getCandidateProfile();
    }

    /**
     * 兼容方法：jobContext()
     */
    public String jobContext() {
        return getJobContext();
    }

    /**
     * 兼容方法：sessionId()
     */
    public String sessionId() {
        return getSessionId();
    }

    /**
     * 兼容方法：knowledgeCompany()
     */
    public String knowledgeCompany() {
        return getKnowledgeCompany();
    }

    /**
     * 兼容方法：knowledgeJobPosition()
     */
    public String knowledgeJobPosition() {
        return getKnowledgeJobPosition();
    }

    /**
     * 兼容方法：questionType()
     */
    public QuestionType questionType() {
        return getQuestionType();
    }

    /**
     * 兼容方法：questionCount()
     */
    public int questionCount() {
        return getQuestionCount();
    }

    /**
     * 兼容方法：outputKey()
     */
    public String outputKey() {
        return getOutputKey();
    }

    // ========== Getter 方法 ==========

    public String getCandidateProfile() {
        return (String) data().getOrDefault(CANDIDATE_PROFILE, "");
    }

    public String getJobContext() {
        return (String) data().getOrDefault(JOB_CONTEXT, "");
    }

    public String getSessionId() {
        return (String) data().getOrDefault(SESSION_ID, "");
    }

    public String getKnowledgeCompany() {
        return (String) data().getOrDefault(KNOWLEDGE_COMPANY, "");
    }

    public String getKnowledgeJobPosition() {
        return (String) data().getOrDefault(KNOWLEDGE_JOB_POSITION, "");
    }

    public Integer getTechnicalBasicCount() {
        return (Integer) data().getOrDefault(TECHNICAL_BASIC_COUNT, 3);
    }

    public Integer getProjectCount() {
        return (Integer) data().getOrDefault(PROJECT_COUNT, 3);
    }

    public Integer getBusinessCount() {
        return (Integer) data().getOrDefault(BUSINESS_COUNT, 2);
    }

    public Integer getSoftSkillCount() {
        return (Integer) data().getOrDefault(SOFT_SKILL_COUNT, 2);
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getTechnicalBasicQuestions() {
        return (List<GeneratedQuestion>) data().getOrDefault(TECHNICAL_BASIC_QUESTIONS, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getProjectQuestions() {
        return (List<GeneratedQuestion>) data().getOrDefault(PROJECT_QUESTIONS, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getBusinessQuestions() {
        return (List<GeneratedQuestion>) data().getOrDefault(BUSINESS_QUESTIONS, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getSoftSkillQuestions() {
        return (List<GeneratedQuestion>) data().getOrDefault(SOFT_SKILL_QUESTIONS, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getTechnicalQuestionsQueue() {
        return (List<GeneratedQuestion>) data().getOrDefault(TECHNICAL_QUESTIONS_QUEUE, new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    public List<GeneratedQuestion> getBusinessQuestionsQueue() {
        return (List<GeneratedQuestion>) data().getOrDefault(BUSINESS_QUESTIONS_QUEUE, new ArrayList<>());
    }

    public Integer getCurrentTechnicalIndex() {
        return (Integer) data().getOrDefault(CURRENT_TECHNICAL_INDEX, 0);
    }

    public Integer getCurrentBusinessIndex() {
        return (Integer) data().getOrDefault(CURRENT_BUSINESS_INDEX, 0);
    }

    public Boolean getFallback() {
        return (Boolean) data().getOrDefault(FALLBACK, false);
    }

    public QuestionType getQuestionType() {
        return (QuestionType) data().get(QUESTION_TYPE);
    }

    public Integer getQuestionCount() {
        Object value = data().get(QUESTION_COUNT);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public String getOutputKey() {
        return (String) data().getOrDefault(OUTPUT_KEY, "");
    }
}