package com.zunff.interview.service.extend;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.FollowUpDecisionBO;
import com.zunff.interview.model.dto.analysis.FrameWithTimestamp;
import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import com.zunff.interview.model.dto.llm.vars.FollowUpDecisionUserPromptVars;
import com.zunff.interview.model.dto.llm.resp.EvaluationResponseDto;
import com.zunff.interview.model.dto.llm.resp.FollowUpDecisionResponseDto;
import com.zunff.interview.utils.JsonExtractionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多模态分析服务
 * 核心方法：comprehensiveOmniEvaluation() — 一次 Omni 调用综合评估
 * 辅助方法：decideFollowUpSimple() — 基于评估结果进行追问决策
 *
 * 模型分工：
 * - omniModalService (qwen3.5-omni-plus): Omni 多模态综合评估（视频帧+音频+文本）
 * - textChatClient (qwen3.5-plus): 追问决策等文本任务
 */
@Slf4j
public class MultimodalAnalysisService {

    private final ChatClient textChatClient;
    private final OmniModalService omniModalService;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;
    private final boolean multimodalEnabled;

    public MultimodalAnalysisService(ChatClient textChatClient,
                                      OmniModalService omniModalService,
                                      PromptTemplateService promptTemplateService,
                                      PromptConfig promptConfig,
                                      boolean multimodalEnabled) {
        this.textChatClient = textChatClient;
        this.omniModalService = omniModalService;
        this.promptTemplateService = promptTemplateService;
        this.promptConfig = promptConfig;
        this.multimodalEnabled = multimodalEnabled;
    }

    // ========== 核心方法 ==========

    /**
     * Omni 多模态综合评估（一次性调用）
     * 将转录文本+时间戳、视频帧+时间戳、原始音频WAV一起发给 Qwen-Omni 进行综合评估
     * 当 multimodalEnabled=false 时，降级为纯文本评估（qwen-plus）
     *
     * @param question              当前问题
     * @param transcribedText       转录文本
     * @param transcriptEntries     转录条目（带时间戳）
     * @param framesWithTimestamps  视频帧（带时间戳）
     * @param base64WavAudio        WAV音频（Base64编码）
     * @param evaluationPromptName  评估Prompt模板名称
     * @return 综合评估结果
     */
    public EvaluationBO comprehensiveOmniEvaluation(
            String question,
            String transcribedText,
            List<TranscriptEntry> transcriptEntries,
            List<FrameWithTimestamp> framesWithTimestamps,
            String base64WavAudio,
            String evaluationPromptName) {

        boolean hasFrames = framesWithTimestamps != null && !framesWithTimestamps.isEmpty();
        boolean hasAudio = base64WavAudio != null && !base64WavAudio.isEmpty();

        log.info("开始综合评估，multimodalEnabled={}, 视频帧: {}, 音频: {}, 转录条目: {}",
                multimodalEnabled,
                hasFrames ? framesWithTimestamps.size() : 0,
                hasAudio ? "有" : "无",
                transcriptEntries != null ? transcriptEntries.size() : 0);

        // multimodalEnabled=false 或无任何多模态数据时，降级为纯文本评估
        if (!multimodalEnabled || (!hasFrames && !hasAudio)) {
            log.info("多模态未启用或无多模态数据，使用纯文本评估");
            return textOnlyEvaluation(question, transcribedText, evaluationPromptName);
        }

        try {
            // 加载评估模板
            String systemPrompt = promptTemplateService.getPrompt("evaluation-omni-" + evaluationPromptName);

            String userPrompt = promptTemplateService.getPrompt("omni-evaluation-user", Map.of(
                    "question", question == null ? "" : question,
                    "candidateAnswer", formatTranscriptEntries(transcriptEntries, transcribedText),
                    "frameTimestamps", hasFrames ? formatFrameTimestamps(framesWithTimestamps) : "无关键帧时间戳"
            ));

            // 3. 提取纯帧数据用于 Omni 调用
            List<String> base64Frames = framesWithTimestamps.stream()
                    .map(FrameWithTimestamp::getFrame)
                    .toList();

            // 4. 调用 OmniModalService 进行多模态综合分析
            String response = omniModalService.analyzeMultimodal(base64Frames, base64WavAudio, systemPrompt, userPrompt);

            // 5. 解析返回的 JSON
            return parseOmniEvaluationResponse(response);

        } catch (Exception e) {
            log.error("Omni多模态综合评估失败", e);
            throw new RuntimeException("Omni多模态综合评估失败", e);
        }
    }

    /**
     * 纯文本评估（降级方案）
     * 使用 qwen-plus 文本模型进行评估，多模态分数字段返回默认值
     */
    private EvaluationBO textOnlyEvaluation(String question, String transcribedText, String evaluationPromptName) {
        try {
            // 加载评估模板
            String systemPrompt = promptTemplateService.getPrompt("evaluation-omni-" + evaluationPromptName);

            String userPrompt = promptTemplateService.getPrompt("omni-text-evaluation-user", Map.of(
                    "question", question == null ? "" : question,
                    "candidateAnswer", transcribedText == null ? "" : transcribedText
            ));

            EvaluationResponseDto response = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(EvaluationResponseDto.class);

            return toEvaluationBO(response);

        } catch (Exception e) {
            log.error("纯文本评估失败", e);
            throw new RuntimeException("纯文本评估失败", e);
        }
    }

    /**
     * 简化版追问决策（基于 EvaluationBO 中的多模态分数字段）
     *
     * @param question      当前问题
     * @param answer        候选人回答
     * @param evaluation    评估结果（包含 emotionScore/bodyLanguageScore/voiceToneScore）
     * @param followUpCount 已追问次数
     * @param maxFollowUps  追问上限
     * @return 追问决策结果
     */
    public FollowUpDecisionBO decideFollowUpSimple(
            String question,
            String answer,
            EvaluationBO evaluation,
            int followUpCount,
            int maxFollowUps) {

        log.info("开始追问决策，当前追问次数: {}/{}", followUpCount, maxFollowUps);

        String systemPrompt = promptTemplateService.getPrompt("followup-decision", Map.of(
                "responseLanguage", promptConfig.getResponseLanguage()
        ));

        String userPrompt = promptTemplateService.getPrompt("followup-decision-user",
                new FollowUpDecisionUserPromptVars(
                        question == null ? "" : question,
                        answer == null ? "" : answer,
                        evaluation.getOverallScore(),
                        evaluation.getAccuracy(),
                        evaluation.getLogic(),
                        evaluation.getFluency(),
                        evaluation.getConfidence(),
                        String.join(", ", evaluation.getStrengths()),
                        String.join(", ", evaluation.getWeaknesses()),
                        evaluation.getDetailedEvaluation() == null ? "" : evaluation.getDetailedEvaluation(),
                        evaluation.getEmotionScore(),
                        evaluation.getBodyLanguageScore(),
                        evaluation.getVoiceToneScore(),
                        evaluation.isModalityConcern(),
                        evaluation.getModalityFollowUpSuggestion() == null ? "" : evaluation.getModalityFollowUpSuggestion(),
                        followUpCount,
                        maxFollowUps,
                        promptConfig.getResponseLanguage()
                ).asMap()
        );

        try {
            FollowUpDecisionResponseDto response = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(FollowUpDecisionResponseDto.class);

            return toFollowUpDecisionBO(response);

        } catch (Exception e) {
            log.error("追问决策失败", e);
            return FollowUpDecisionBO.builder()
                    .decision("nextQuestion")
                    .reason("追问决策失败，跳过追问")
                    .build();
        }
    }

    // ========== 解析方法 ==========

    /**
     * 解析 Omni 多模态评估响应
     */
    private EvaluationBO parseOmniEvaluationResponse(String response) {
        try {
            String jsonStr = JsonExtractionUtils.extractJsonObjectString(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            // 提取多模态分数字段
            int emotionScore = json.getInt("emotionScore", 70);
            int bodyLanguageScore = json.getInt("bodyLanguageScore", 70);
            int voiceToneScore = json.getInt("voiceToneScore", 70);

            int accuracy = json.getInt("accuracy", 70);
            int logic = json.getInt("logic", 70);
            int fluency = json.getInt("fluency", 70);
            int confidence = json.getInt("confidence", 70);

            return EvaluationBO.builder()
                    .accuracy(accuracy)
                    .logic(logic)
                    .fluency(fluency)
                    .confidence(confidence)
                    .emotionScore(emotionScore)
                    .bodyLanguageScore(bodyLanguageScore)
                    .voiceToneScore(voiceToneScore)
                    .overallScore(json.getInt("overallScore", 70))
                    .strengths(parseStringList(json, "strengths"))
                    .weaknesses(parseStringList(json, "weaknesses"))
                    .detailedEvaluation(json.getStr("detailedEvaluation", ""))
                    .modalityFollowUpSuggestion(buildOmniFollowUpSuggestion(emotionScore, bodyLanguageScore, voiceToneScore))
                    .modalityConcern(emotionScore < 60 || bodyLanguageScore < 60 || voiceToneScore < 60)
                    .build();

        } catch (Exception e) {
            log.error("解析Omni评估结果失败: {}", e.getMessage());
            return EvaluationBO.builder()
                    .accuracy(60).logic(60).fluency(60).confidence(60)
                    .emotionScore(70).bodyLanguageScore(70).voiceToneScore(70)
                    .overallScore(60)
                    .build();
        }
    }

    /**
     * 根据多模态分值返回追问建议
     */
    private String buildOmniFollowUpSuggestion(int emotionScore, int bodyLanguageScore, int voiceToneScore) {
        StringBuilder suggestion = new StringBuilder();

        if (emotionScore < 60) {
            suggestion.append("表情得分较低(").append(emotionScore).append(")，可能存在紧张或不自信");
        }

        if (bodyLanguageScore < 60) {
            if (suggestion.length() > 0) suggestion.append("；");
            suggestion.append("肢体语言得分较低(").append(bodyLanguageScore).append(")，可能缺乏自信或沟通经验");
        }

        if (voiceToneScore < 60) {
            if (suggestion.length() > 0) suggestion.append("；");
            suggestion.append("语音得分较低(").append(voiceToneScore).append(")，可能语速不稳、语气不自信或有过多的停顿");
        }

        return suggestion.toString();
    }

    private EvaluationBO toEvaluationBO(EvaluationResponseDto response) {
        if (response == null) {
            return EvaluationBO.builder()
                    .accuracy(60).logic(60).fluency(60).confidence(60)
                    .emotionScore(70).bodyLanguageScore(70).voiceToneScore(70)
                    .overallScore(60)
                    .build();
        }
        int emotionScore = response.emotionScore() == null ? 70 : response.emotionScore();
        int bodyLanguageScore = response.bodyLanguageScore() == null ? 70 : response.bodyLanguageScore();
        int voiceToneScore = response.voiceToneScore() == null ? 70 : response.voiceToneScore();
        return EvaluationBO.builder()
                .accuracy(response.accuracy() == null ? 70 : response.accuracy())
                .logic(response.logic() == null ? 70 : response.logic())
                .fluency(response.fluency() == null ? 70 : response.fluency())
                .confidence(response.confidence() == null ? 70 : response.confidence())
                .emotionScore(emotionScore)
                .bodyLanguageScore(bodyLanguageScore)
                .voiceToneScore(voiceToneScore)
                .overallScore(response.overallScore() == null ? 70 : response.overallScore())
                .strengths(response.strengths() == null ? List.of() : response.strengths())
                .weaknesses(response.weaknesses() == null ? List.of() : response.weaknesses())
                .detailedEvaluation(response.detailedEvaluation() == null ? "" : response.detailedEvaluation())
                .modalityFollowUpSuggestion(buildOmniFollowUpSuggestion(emotionScore, bodyLanguageScore, voiceToneScore))
                .modalityConcern(emotionScore < 60 || bodyLanguageScore < 60 || voiceToneScore < 60)
                .build();
    }

    private FollowUpDecisionBO toFollowUpDecisionBO(FollowUpDecisionResponseDto response) {
        if (response == null) {
            return FollowUpDecisionBO.builder()
                    .decision(RouteDecision.NEXT_QUESTION.getValue())
                    .reason("Empty decision response")
                    .build();
        }
        return FollowUpDecisionBO.builder()
                .decision(resolveDecision(response))
                .followUpQuestion(response.followUpQuestion() == null ? "" : response.followUpQuestion())
                .reason(response.reason() == null ? "" : response.reason())
                .followUpType(resolveFollowUpType(response))
                .build();
    }

    private List<String> parseStringList(JSONObject json, String key) {
        List<String> result = new ArrayList<>();
        JSONArray array = json.getJSONArray(key);
        if (array != null && !array.isEmpty()) {
            for (int i = 0; i < array.size(); i++) {
                result.add(array.getStr(i));
            }
        }
        return result;
    }

    private String formatTranscriptEntries(List<TranscriptEntry> transcriptEntries, String transcribedText) {
        if (transcriptEntries != null && !transcriptEntries.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (TranscriptEntry entry : transcriptEntries) {
                builder.append(String.format("[%d-%d] %s%n",
                        entry.getStartTimeMs(),
                        entry.getEndTimeMs(),
                        entry.getText()));
            }
            return builder.toString().trim();
        }
        return transcribedText == null ? "" : transcribedText;
    }

    private String formatFrameTimestamps(List<FrameWithTimestamp> framesWithTimestamps) {
        if (framesWithTimestamps == null || framesWithTimestamps.isEmpty()) {
            return "无关键帧时间戳";
        }
        StringBuilder builder = new StringBuilder();
        for (FrameWithTimestamp frame : framesWithTimestamps) {
            builder.append("- 帧时间戳: ").append(frame.getTimestampMs()).append("ms").append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String resolveDecision(FollowUpDecisionResponseDto response) {
        Integer decisionCode = response.decisionCode();
        if (decisionCode != null) {
            return switch (decisionCode) {
                case 1 -> RouteDecision.DEEP_DIVE.getValue();
                case 2 -> RouteDecision.CHALLENGE_MODE.getValue();
                case 3 -> RouteDecision.FOLLOW_UP.getValue();
                case 4 -> RouteDecision.NEXT_QUESTION.getValue();
                default -> RouteDecision.NEXT_QUESTION.getValue();
            };
        }
        String decision = response.decision();
        if (decision == null || decision.isBlank()) {
            return RouteDecision.NEXT_QUESTION.getValue();
        }
        return decision;
    }

    private String resolveFollowUpType(FollowUpDecisionResponseDto response) {
        Integer followUpTypeCode = response.followUpTypeCode();
        if (followUpTypeCode != null) {
            return switch (followUpTypeCode) {
                case 1 -> "deep_dive";
                case 2 -> "validate_clarify";
                case 3 -> "add_detail";
                case 4 -> "explore_anomaly";
                default -> "";
            };
        }
        return response.followUpType();
    }

}
