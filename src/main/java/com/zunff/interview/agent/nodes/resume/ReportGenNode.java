package com.zunff.interview.agent.nodes.resume;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenNode {

    private final ChatClient textChatClient;
    private final PromptTemplateService promptTemplateService;
    private final SseEmitterRegistry emitterRegistry;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        log.info("[ReportGen] 生成报告和雷达图, sessionId: {}", state.sessionId());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "report", "running");

        Map<String, Object> updates = new HashMap<>();

        // 1. 生成雷达图 JSON（确定性计算，不需要 LLM）
        generateRadarJson(state, updates);

        // 2. 生成 Markdown 报告（需要 LLM）
        generateReport(state, updates);

        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "report", "completed");
        return CompletableFuture.completedFuture(updates);
    }

    private void generateRadarJson(ResumeState state, Map<String, Object> updates) {
        try {
            JSONArray dimensions = new JSONArray();
            int totalScore = 0;
            int count = 0;

            for (String scoreJson : new String[]{
                    state.skillScore(), state.expScore(), state.bgScore(), state.potentialScore()
            }) {
                if (scoreJson != null && !scoreJson.isEmpty() && !scoreJson.equals("{}")) {
                    try {
                        JSONObject json = JSONUtil.parseObj(scoreJson);
                        dimensions.add(json);
                        totalScore += json.getInt("score", 0);
                        count++;
                    } catch (Exception e) {
                        log.warn("解析维度评分失败: {}", scoreJson);
                    }
                }
            }

            int overallScore = count > 0 ? (int) Math.round((double) totalScore / count) : 0;
            String overallLevel = determineLevel(overallScore);

            JSONObject radarJson = new JSONObject();
            radarJson.set("dimensions", dimensions);
            radarJson.set("overallScore", overallScore);
            radarJson.set("overallLevel", overallLevel);

            String result = radarJson.toString();
            updates.put(ResumeState.RADAR_CHART_JSON, result);
            SseHelper.sendRadarChart(emitterRegistry.get(state.sessionId()), result);
            log.info("[ReportGen] 雷达图 JSON 生成完成, overallScore: {}", overallScore);
        } catch (Exception e) {
            log.error("[ReportGen] 雷达图 JSON 生成失败", e);
        }
    }

    private void generateReport(ResumeState state, Map<String, Object> updates) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("resumeText", state.resumeText());
            vars.put("companyInfo", state.companyInfo() != null ? state.companyInfo() : "");
            vars.put("skillScore", state.skillScore() != null ? state.skillScore() : "{}");
            vars.put("expScore", state.expScore() != null ? state.expScore() : "{}");
            vars.put("bgScore", state.bgScore() != null ? state.bgScore() : "{}");
            vars.put("potentialScore", state.potentialScore() != null ? state.potentialScore() : "{}");
            vars.put("currentDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            String promptText = promptTemplateService.getPrompt("resume-report-gen", vars);

            String result = textChatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            if (result == null) result = "";
            updates.put(ResumeState.REPORT_MARKDOWN, result);
            log.info("[ReportGen] 报告生成完成, 长度: {}", result.length());
        } catch (Exception e) {
            log.error("[ReportGen] 报告生成失败", e);
            updates.put(ResumeState.REPORT_MARKDOWN, "# 分析报告\n\n报告生成失败，请稍后重试。");
        }
    }

    private String determineLevel(int score) {
        if (score >= 90) return "EXPERT";
        if (score >= 75) return "SENIOR";
        if (score >= 60) return "MID";
        return "JUNIOR";
    }
}
