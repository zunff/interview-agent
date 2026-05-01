package com.zunff.interview.agent.state;

import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;

import java.util.HashMap;
import java.util.Map;

@Getter
public class ResumeState extends AgentState {

    // ========== 输入 ==========
    public static final String SESSION_ID = "sessionId";
    public static final String FILE_PATH = "filePath";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_TYPE = "fileType";

    // ========== 解析 ==========
    public static final String RESUME_TEXT = "resumeText";

    // ========== 公司研究 ==========
    public static final String COMPANY_INFO = "companyInfo";

    // ========== 四维度评估 ==========
    public static final String SKILL_SCORE = "skillScore";
    public static final String EXP_SCORE = "expScore";
    public static final String BG_SCORE = "bgScore";
    public static final String POTENTIAL_SCORE = "potentialScore";

    // ========== 输出 ==========
    public static final String REPORT_MARKDOWN = "reportMarkdown";
    public static final String RADAR_CHART_JSON = "radarChartJson";
    public static final String ANALYSIS_ID = "analysisId";

    // ========== 错误 ==========
    public static final String ERROR = "error";

    private static class LastValueReducer<T> implements Reducer<T> {
        @Override
        public T apply(T currentValue, T newValue) {
            return newValue;
        }
    }

    public static final Map<String, Channel<?>> SCHEMA = new HashMap<>();

    static {
        SCHEMA.put(SESSION_ID, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(FILE_PATH, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(FILE_NAME, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(FILE_TYPE, Channels.base(new LastValueReducer<>(), () -> ""));

        SCHEMA.put(RESUME_TEXT, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(COMPANY_INFO, Channels.base(new LastValueReducer<>(), () -> ""));

        SCHEMA.put(SKILL_SCORE, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(EXP_SCORE, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(BG_SCORE, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(POTENTIAL_SCORE, Channels.base(new LastValueReducer<>(), () -> ""));

        SCHEMA.put(REPORT_MARKDOWN, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(RADAR_CHART_JSON, Channels.base(new LastValueReducer<>(), () -> ""));
        SCHEMA.put(ANALYSIS_ID, Channels.base(new LastValueReducer<>(), () -> ""));

        // SSE_EMITTER 不在 SCHEMA 中，通过 initial state 传入
        SCHEMA.put(ERROR, Channels.base(new LastValueReducer<>(), () -> ""));
    }

    public ResumeState(Map<String, Object> initData) {
        super(initData);
    }

    // ========== 类型安全的访问器 ==========

    public String sessionId() {
        return get(SESSION_ID);
    }

    public String filePath() {
        return get(FILE_PATH);
    }

    public String fileName() {
        return get(FILE_NAME);
    }

    public String fileType() {
        return get(FILE_TYPE);
    }

    public String resumeText() {
        return get(RESUME_TEXT);
    }

    public String companyInfo() {
        return get(COMPANY_INFO);
    }

    public String skillScore() {
        return get(SKILL_SCORE);
    }

    public String expScore() {
        return get(EXP_SCORE);
    }

    public String bgScore() {
        return get(BG_SCORE);
    }

    public String potentialScore() {
        return get(POTENTIAL_SCORE);
    }

    public String reportMarkdown() {
        return get(REPORT_MARKDOWN);
    }

    public String radarChartJson() {
        return get(RADAR_CHART_JSON);
    }

    public String analysisId() {
        return get(ANALYSIS_ID);
    }

    public String error() {
        return get(ERROR);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key) {
        return (T) data().getOrDefault(key, null);
    }
}
