package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.model.bo.InterviewQuestionBO;
import com.zunff.interview.model.bo.LevelMatchResult;
import com.zunff.interview.model.dto.llm.resp.ReportResponseDto;
import com.zunff.interview.model.dto.llm.vars.ReportUserPromptVars;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 报告生成节点
 * 面试结束后生成综合评估报告，并通过 WebSocket 推送给前端
 */
@Slf4j
@Component
public class ReportGeneratorNode {

    private static final int MAX_ANSWER_CHARS = 8000;
    private static final int MAX_FOLLOW_UP_CHAIN_CHARS = 12000;

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;
    private final InterviewSessionService sessionService;
    private final InterviewWebSocketHandler webSocketHandler;

    public ReportGeneratorNode(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService,
            PromptConfig promptConfig,
            InterviewSessionService sessionService,
            @Lazy InterviewWebSocketHandler webSocketHandler) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateService = promptTemplateService;
        this.promptConfig = promptConfig;
        this.sessionService = sessionService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 执行报告生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试报告");

        String sessionId = state.sessionId();
        String candidateProfile = state.candidateProfileAsText();

        String jobContext = state.hasJobAnalysisResult()
                ? state.jobAnalysisResult().generateJobSummary()
                : state.jobInfo();

        // 从新的列表获取已评估的题目
        List<InterviewQuestionBO> technicalQuestions = state.evaluatedTechnicalQuestions();
        List<InterviewQuestionBO> businessQuestions = state.evaluatedBusinessQuestions();

        int technicalQuestionsDone = state.technicalQuestionsDone();
        int businessQuestionsDone = state.businessQuestionsDone();
        double technicalAvgScore = calculateAverageScore(technicalQuestions);
        double businessAvgScore = calculateAverageScore(businessQuestions);

        // 计算总体统计
        List<InterviewQuestionBO> allQuestions = new ArrayList<>();
        allQuestions.addAll(technicalQuestions);
        allQuestions.addAll(businessQuestions);

        // 分离主问题和追问计数
        int totalMainQuestions = technicalQuestionsDone + businessQuestionsDone;  // 主问题总数
        int totalFollowUps = (int) allQuestions.stream().filter(q -> q.isFollowUp()).count();  // 追问总数
        int totalQuestions = allQuestions.size();  // 所有题目总数（用于计算平均分）

        double avgScore = 0;
        double avgAccuracy = 0, avgLogic = 0, avgFluency = 0, avgConfidence = 0;
        double avgEmotion = 0, avgBodyLanguage = 0, avgVoiceTone = 0;

        if (totalQuestions > 0) {
            int totalScore = 0, totalAccuracy = 0, totalLogic = 0, totalFluency = 0, totalConfidence = 0;
            int totalEmotion = 0, totalBodyLanguage = 0, totalVoiceTone = 0;

            for (InterviewQuestionBO q : allQuestions) {
                if (q.getOverallScore() != null) totalScore += q.getOverallScore();
                if (q.getAccuracy() != null) totalAccuracy += q.getAccuracy();
                if (q.getLogic() != null) totalLogic += q.getLogic();
                if (q.getFluency() != null) totalFluency += q.getFluency();
                if (q.getConfidence() != null) totalConfidence += q.getConfidence();
                if (q.getEmotionScore() != null) totalEmotion += q.getEmotionScore();
                if (q.getBodyLanguageScore() != null) totalBodyLanguage += q.getBodyLanguageScore();
                if (q.getVoiceToneScore() != null) totalVoiceTone += q.getVoiceToneScore();
            }

            avgScore = (double) totalScore / totalQuestions;
            avgAccuracy = (double) totalAccuracy / totalQuestions;
            avgLogic = (double) totalLogic / totalQuestions;
            avgFluency = (double) totalFluency / totalQuestions;
            avgConfidence = (double) totalConfidence / totalQuestions;
            avgEmotion = (double) totalEmotion / totalQuestions;
            avgBodyLanguage = (double) totalBodyLanguage / totalQuestions;
            avgVoiceTone = (double) totalVoiceTone / totalQuestions;
        }

        String evalSummaryText = allQuestions.isEmpty()
                ? "No evaluation records."
                : buildEvalSummaryForLlm(allQuestions);

        String technicalRoundScoresText = formatScoresList(extractScores(technicalQuestions));
        String businessRoundScoresText = formatScoresList(extractScores(businessQuestions));
        String levelMatchSummary = formatLevelMatchSummary(state);
        String followUpChainText = truncateChain(state.formatFollowUpChain());
        String interviewEndContext = buildInterviewEndContext(state);

        Map<String, Object> systemVars = new HashMap<>();
        systemVars.put("responseLanguage", promptConfig.getResponseLanguage());
        String systemPrompt = promptTemplateService.getPrompt("report-generator", systemVars);

        String userPrompt = promptTemplateService.getPrompt("report-generator-user",
                ReportUserPromptVars.builder()
                        .responseLanguage(promptConfig.getResponseLanguage())
                        .candidateProfile(candidateProfile)
                        .jobInfo(jobContext)
                        .technicalQuestionsDone(technicalQuestionsDone)
                        .businessQuestionsDone(businessQuestionsDone)
                        .technicalAvgScore(technicalAvgScore)
                        .businessAvgScore(businessAvgScore)
                        .avgScore(avgScore)
                        .avgAccuracy(avgAccuracy)
                        .avgLogic(avgLogic)
                        .avgFluency(avgFluency)
                        .avgConfidence(avgConfidence)
                        .avgEmotion(avgEmotion)
                        .avgBodyLanguage(avgBodyLanguage)
                        .avgVoiceTone(avgVoiceTone)
                        .totalQuestions(totalQuestions)
                        .technicalRoundScoresText(technicalRoundScoresText)
                        .businessRoundScoresText(businessRoundScoresText)
                        .levelMatchSummary(levelMatchSummary)
                        .followUpChainText(followUpChainText)
                        .interviewEndContext(interviewEndContext)
                        .evalSummary(evalSummaryText)
                        .build()
                        .asMap());

        try {
            ChatClient chatClient = chatClientBuilder.build();

            ReportResponseDto reportResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(ReportResponseDto.class);

            // 构建详细评估结果（包含标准答案和建议）
            String questionsDetail = allQuestions.isEmpty()
                    ? "No evaluation records."
                    : buildQuestionsDetail(technicalQuestions, businessQuestions);

            // 轮次追问数
            int technicalFollowUps = (int) technicalQuestions.stream().filter(q -> q.isFollowUp()).count();
            int businessFollowUps = (int) businessQuestions.stream().filter(q -> q.isFollowUp()).count();

            // 使用模板生成报告
            Map<String, Object> headerVars = new HashMap<>();
            headerVars.put("avgScore", String.format("%.1f", avgScore));
            headerVars.put("totalMainQuestions", String.valueOf(totalMainQuestions));
            headerVars.put("technicalQuestionsDone", String.valueOf(technicalQuestionsDone));
            headerVars.put("businessQuestionsDone", String.valueOf(businessQuestionsDone));
            headerVars.put("totalFollowUps", String.valueOf(totalFollowUps));
            headerVars.put("avgAccuracy", String.format("%.1f", avgAccuracy));
            headerVars.put("avgLogic", String.format("%.1f", avgLogic));
            headerVars.put("avgFluency", String.format("%.1f", avgFluency));
            headerVars.put("avgConfidence", String.format("%.1f", avgConfidence));
            headerVars.put("avgEmotion", String.format("%.1f", avgEmotion));
            headerVars.put("avgBodyLanguage", String.format("%.1f", avgBodyLanguage));
            headerVars.put("avgVoiceTone", String.format("%.1f", avgVoiceTone));
            headerVars.put("technicalFollowUps", String.valueOf(technicalFollowUps));
            headerVars.put("businessFollowUps", String.valueOf(businessFollowUps));
            headerVars.put("technicalAvgScore", String.format("%.1f", technicalAvgScore));
            headerVars.put("businessAvgScore", String.format("%.1f", businessAvgScore));
            headerVars.put("llmGeneratedContent", reportResponse.toMarkdown());
            headerVars.put("questionsDetail", questionsDetail);
            headerVars.put("hiringRecommendation", reportResponse.formatRecommendation());

            String fullReport = promptTemplateService.getTemplate("templates/report-header.md", headerVars);

            sessionService.saveReport(sessionId, fullReport);

            webSocketHandler.sendFinalReport(sessionId, fullReport);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.IS_FINISHED, true);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("面试报告生成完成，平均得分: {}", avgScore);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成报告失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.IS_FINISHED, true);
            return CompletableFuture.completedFuture(updates);
        }
    }

    private String buildQuestionsDetail(
            List<InterviewQuestionBO> technicalQuestions,
            List<InterviewQuestionBO> businessQuestions) {

        StringBuilder sb = new StringBuilder();
        int mainQNum = 0;

        // 技术轮
        if (!technicalQuestions.isEmpty()) {
            sb.append("### 技术轮\n\n");
            Map<Integer, List<InterviewQuestionBO>> techFollowUpsMap = buildFollowUpsMap(technicalQuestions);
            for (InterviewQuestionBO q : technicalQuestions) {
                if (!q.isFollowUp()) {
                    mainQNum++;
                    sb.append(buildQuestionFromTemplate(mainQNum, q, techFollowUpsMap.get(q.getQuestionIndex())));
                }
            }
        }

        // 业务轮
        if (!businessQuestions.isEmpty()) {
            sb.append("### 业务轮\n\n");
            Map<Integer, List<InterviewQuestionBO>> bizFollowUpsMap = buildFollowUpsMap(businessQuestions);
            for (InterviewQuestionBO q : businessQuestions) {
                if (!q.isFollowUp()) {
                    mainQNum++;
                    sb.append(buildQuestionFromTemplate(mainQNum, q, bizFollowUpsMap.get(q.getQuestionIndex())));
                }
            }
        }

        return sb.toString();
    }

    private Map<Integer, List<InterviewQuestionBO>> buildFollowUpsMap(List<InterviewQuestionBO> questions) {
        Map<Integer, List<InterviewQuestionBO>> followUpsMap = new HashMap<>();
        for (InterviewQuestionBO q : questions) {
            if (q.isFollowUp()) {
                followUpsMap.computeIfAbsent(q.getQuestionIndex(), k -> new ArrayList<>()).add(q);
            }
        }
        return followUpsMap;
    }

    private String buildQuestionFromTemplate(int qNum, InterviewQuestionBO mainQ, List<InterviewQuestionBO> followUps) {
        Map<String, Object> vars = new HashMap<>();

        // 基本信息
        vars.put("questionNum", String.valueOf(qNum));
        vars.put("questionTitle", nullToEmpty(mainQ.getQuestion()));
        vars.put("questionType", nullToEmpty(mainQ.getQuestionType()));
        vars.put("difficulty", nullToEmpty(mainQ.getDifficulty()));

        // 标准答案（条件渲染，包含标题和内容）
        vars.put("standardAnswer",
                hasContent(mainQ.getStandardAnswer())
                        ? "**标准答案：**\n" + mainQ.getStandardAnswer() + "\n\n"
                        : "");

        // 改进建议（条件渲染）
        vars.put("suggestions",
                hasContent(mainQ.getSuggestions())
                        ? "**改进建议：**\n" + mainQ.getSuggestions() + "\n\n"
                        : "");

        // 期望关键词（条件渲染）
        vars.put("expectedKeywords",
                !CollectionUtils.isEmpty(mainQ.getExpectedKeywords())
                        ? "**期望关键词：** " + String.join(", ", mainQ.getExpectedKeywords()) + "\n\n"
                        : "");

        // 多模态异常（条件渲染，带分数区间描述）
        vars.put("modalityConcern", buildDetailedModalityWarning(mainQ));

        // 回答和得分
        vars.put("answer", nullToEmpty(mainQ.getAnswer()));
        vars.put("overallScore", String.valueOf(mainQ.getOverallScore()));

        // 评分表格
        vars.put("accuracy", String.valueOf(mainQ.getAccuracy()));
        vars.put("logic", String.valueOf(mainQ.getLogic()));
        vars.put("fluency", String.valueOf(mainQ.getFluency()));
        vars.put("confidence", String.valueOf(mainQ.getConfidence()));
        vars.put("emotionScore", String.valueOf(mainQ.getEmotionScore()));
        vars.put("bodyLanguageScore", String.valueOf(mainQ.getBodyLanguageScore()));
        vars.put("voiceTone", String.valueOf(mainQ.getVoiceToneScore()));

        // 追问（条件渲染）
        vars.put("followUps", formatFollowUps(followUps));

        return promptTemplateService.getTemplate("templates/report-question.md", vars);
    }

    private String formatFollowUps(List<InterviewQuestionBO> followUps) {
        if (CollectionUtils.isEmpty(followUps)) {
            return "";
        }
        StringBuilder sb = new StringBuilder("**追问：**\n");
        for (InterviewQuestionBO f : followUps) {
            sb.append("- ").append(f.getQuestion()).append("\n");
            sb.append("  - **得分:** ").append(f.getOverallScore()).append("/100\n");
            sb.append("  - **回答:** ").append(nullToEmpty(f.getAnswer())).append("\n");
            if (hasContent(f.getStandardAnswer())) {
                sb.append("  - **标准答案:** ").append(f.getStandardAnswer()).append("\n");
            }
            if (hasContent(f.getSuggestions())) {
                sb.append("  - **改进建议:** ").append(f.getSuggestions()).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildEvalSummaryForLlm(List<InterviewQuestionBO> questions) {
        StringBuilder evalSummary = new StringBuilder();

        int mainQNum = 0;
        for (InterviewQuestionBO q : questions) {
            if (!q.isFollowUp()) {
                mainQNum++;
                evalSummary.append("\n### Question ").append(mainQNum).append("\n");
                evalSummary.append("- Question type: ").append(q.getQuestionType()).append("\n");
                evalSummary.append("- Difficulty: ").append(q.getDifficulty()).append("\n");

                if (q.getStandardAnswer() != null && !q.getStandardAnswer().isEmpty()) {
                    evalSummary.append("- Standard Answer: ").append(q.getStandardAnswer()).append("\n");
                }
                if (q.getSuggestions() != null && !q.getSuggestions().isEmpty()) {
                    evalSummary.append("- Suggestions: ").append(q.getSuggestions()).append("\n");
                }
                if (!CollectionUtils.isEmpty(q.getExpectedKeywords())) {
                    evalSummary.append("- Expected keywords: ").append(String.join(", ", q.getExpectedKeywords())).append("\n");
                }
                if (Boolean.TRUE.equals(q.getModalityConcern())) {
                    evalSummary.append("- Modality concern: yes\n");
                }

                evalSummary.append("- Question: ").append(nullToEmpty(q.getQuestion())).append("\n");
                evalSummary.append("- Answer: ").append(truncate(nullToEmpty(q.getAnswer()), MAX_ANSWER_CHARS)).append("\n");
                evalSummary.append("- Overall score: ").append(q.getOverallScore()).append("\n");
                evalSummary.append("  - Content: accuracy ").append(q.getAccuracy());
                evalSummary.append(" | logic ").append(q.getLogic());
                evalSummary.append(" | fluency ").append(q.getFluency());
                evalSummary.append(" | confidence ").append(q.getConfidence()).append("\n");
                evalSummary.append("  - Multimodal: emotion ").append(q.getEmotionScore());
                evalSummary.append(" | body language ").append(q.getBodyLanguageScore());
                evalSummary.append(" | voice tone ").append(q.getVoiceToneScore()).append("\n");
                evalSummary.append("- Detailed evaluation: ").append(nullToEmpty(q.getDetailedEvaluation())).append("\n");
                if (!CollectionUtils.isEmpty(q.getStrengths())) {
                    evalSummary.append("  - Strengths: ").append(String.join(", ", q.getStrengths())).append("\n");
                }
                if (!CollectionUtils.isEmpty(q.getWeaknesses())) {
                    evalSummary.append("  - Weaknesses: ").append(String.join(", ", q.getWeaknesses())).append("\n");
                }
            } else {
                evalSummary.append("  - Follow-up: ").append(q.getQuestion()).append("\n");
                evalSummary.append("    - Score: ").append(q.getOverallScore()).append("\n");
                evalSummary.append("    - Answer: ").append(truncate(nullToEmpty(q.getAnswer()), MAX_ANSWER_CHARS / 2)).append("\n");
                if (q.getStandardAnswer() != null && !q.getStandardAnswer().isEmpty()) {
                    evalSummary.append("    - Standard Answer: ").append(q.getStandardAnswer()).append("\n");
                }
                if (q.getSuggestions() != null && !q.getSuggestions().isEmpty()) {
                    evalSummary.append("    - Suggestions: ").append(q.getSuggestions()).append("\n");
                }
            }
        }

        return evalSummary.toString();
    }

    
    private double calculateAverageScore(List<InterviewQuestionBO> questions) {
        if (questions.isEmpty()) return 0;
        return questions.stream()
                .filter(q -> q.getOverallScore() != null)
                .mapToInt(InterviewQuestionBO::getOverallScore)
                .average()
                .orElse(0);
    }

    private List<Integer> extractScores(List<InterviewQuestionBO> questions) {
        List<Integer> scores = new ArrayList<>();
        for (InterviewQuestionBO q : questions) {
            if (!q.isFollowUp() && q.getOverallScore() != null) {
                scores.add(q.getOverallScore());
            }
        }
        return scores;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static boolean hasContent(String s) {
        return s != null && !s.isEmpty();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...[truncated]";
    }

    private static String formatScoresList(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return "(none)";
        }
        return scores.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String formatLevelMatchSummary(InterviewState state) {
        if (!state.hasLevelMatchResult()) {
            return "Not available";
        }
        LevelMatchResult lm = state.levelMatchResult();
        if (lm == null) {
            return "Not available";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Position level: ")
                .append(lm.positionLevel() != null ? lm.positionLevel().getDescription() : "unknown")
                .append("\n");
        sb.append("Candidate level: ")
                .append(lm.candidateLevel() != null ? lm.candidateLevel().getDescription() : "unknown")
                .append("\n");
        sb.append("Level mismatch: ").append(lm.isLevelMismatch()).append("\n");
        sb.append("Candidate lower than position: ").append(lm.isCandidateLower()).append("\n");
        sb.append("Difficulty range: ").append(lm.getDifficultyRange()).append("\n");
        if (lm.difficultyPreference() != null) {
            sb.append("Difficulty preference: ").append(lm.difficultyPreference()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String buildInterviewEndContext(InterviewState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Technical round queue exhausted: ").append(state.isTechnicalRoundComplete()).append("\n");
        sb.append("Business round queue exhausted: ").append(state.isBusinessRoundComplete()).append("\n");
        sb.append("Technical questions answered (counter): ").append(state.technicalQuestionsDone()).append("\n");
        sb.append("Business questions answered (counter): ").append(state.businessQuestionsDone()).append("\n");
        return sb.toString().trim();
    }

    private static String truncateChain(String chain) {
        if (chain == null || chain.isEmpty()) {
            return "No follow-up history";
        }
        if (chain.length() <= MAX_FOLLOW_UP_CHAIN_CHARS) {
            return chain;
        }
        return chain.substring(0, MAX_FOLLOW_UP_CHAIN_CHARS) + "\n...[truncated]";
    }

    /**
     * 构建详细的多模态异常警告（带分数区间描述）
     */
    private String buildDetailedModalityWarning(InterviewQuestionBO mainQ) {
        if (!Boolean.TRUE.equals(mainQ.getModalityConcern())) {
            return "";
        }

        List<String> warnings = new ArrayList<>();

        // 表情异常
        if (mainQ.getEmotionScore() != null && mainQ.getEmotionScore() < 60) {
            String desc = getEmotionScoreDesc(mainQ.getEmotionScore());
            warnings.add(String.format("表情（%d分，%s）", mainQ.getEmotionScore(), desc));
        }

        // 肢体语言异常
        if (mainQ.getBodyLanguageScore() != null && mainQ.getBodyLanguageScore() < 60) {
            String desc = getBodyLanguageScoreDesc(mainQ.getBodyLanguageScore());
            warnings.add(String.format("肢体语言（%d分，%s）", mainQ.getBodyLanguageScore(), desc));
        }

        // 语音语调异常
        if (mainQ.getVoiceToneScore() != null && mainQ.getVoiceToneScore() < 60) {
            String desc = getVoiceToneScoreDesc(mainQ.getVoiceToneScore());
            warnings.add(String.format("语音语调（%d分，%s）", mainQ.getVoiceToneScore(), desc));
        }

        if (warnings.isEmpty()) {
            return "";
        }

        return "**⚠️ 多模态异常：** 检测到 " + String.join("、", warnings) + "\n\n";
    }

    private String getEmotionScoreDesc(int score) {
        if (score >= 70) return "正常表情";
        if (score >= 50) return "轻微异常（如紧张）";
        if (score >= 30) return "明显异常（如焦虑）";
        return "严重异常";
    }

    private String getBodyLanguageScoreDesc(int score) {
        if (score >= 70) return "自然肢体";
        if (score >= 50) return "轻微僵硬";
        if (score >= 30) return "明显不自然";
        return "严重异常（如颤抖）";
    }

    private String getVoiceToneScoreDesc(int score) {
        if (score >= 70) return "稳定语调";
        if (score >= 50) return "轻微波动";
        if (score >= 30) return "明显紧张";
        return "严重异常（如结巴）";
    }
}
