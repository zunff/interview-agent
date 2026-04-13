package com.zunff.interview.service.extend;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.FollowUpDecisionBO;
import com.zunff.interview.model.dto.analysis.FrameWithTimestamp;
import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;

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
    private final boolean multimodalEnabled;

    public MultimodalAnalysisService(ChatClient textChatClient,
                                      OmniModalService omniModalService,
                                      PromptTemplateService promptTemplateService,
                                      boolean multimodalEnabled) {
        this.textChatClient = textChatClient;
        this.omniModalService = omniModalService;
        this.promptTemplateService = promptTemplateService;
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
            // 1. 加载评估 Prompt
            String systemPrompt = promptTemplateService.getPrompt(evaluationPromptName + "-omni");

            // 2. 构建用户 Prompt，包含问题、转录文本+时间戳、关键帧时间信息
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("## 面试问题\n").append(question).append("\n\n");

            // 添加转录文本和时间戳
            userPrompt.append("## 候选人回答（转录文本+时间戳）\n");
            if (transcriptEntries != null && !transcriptEntries.isEmpty()) {
                for (TranscriptEntry entry : transcriptEntries) {
                    userPrompt.append(String.format("[%d-%d] %s\n",
                            entry.getStartTimeMs(), entry.getEndTimeMs(), entry.getText()));
                }
            } else if (transcribedText != null && !transcribedText.isEmpty()) {
                userPrompt.append(transcribedText);
            }
            userPrompt.append("\n");

            // 添加关键帧时间戳信息（用于和语音时间对齐）
            if (hasFrames) {
                userPrompt.append("## 关键视频帧时间戳（用于和语音时间对齐）\n");
                for (FrameWithTimestamp f : framesWithTimestamps) {
                    userPrompt.append(String.format("- 帧时间戳: %dms\n", f.getTimestampMs()));
                }
                userPrompt.append("\n");
            }

            // 3. 提取纯帧数据用于 Omni 调用
            List<String> base64Frames = framesWithTimestamps.stream()
                    .map(FrameWithTimestamp::getFrame)
                    .toList();

            // 4. 调用 OmniModalService 进行多模态综合分析
            String response = omniModalService.analyzeMultimodal(base64Frames, base64WavAudio, userPrompt.toString());

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
            // 加载旧的纯文本评估 prompt（不包含多模态指令）
            String systemPrompt = promptTemplateService.getPrompt(evaluationPromptName + "-omni");

            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("## 面试问题\n").append(question).append("\n\n");
            userPrompt.append("## 候选人回答（纯文本，无音视频数据）\n");
            if (transcribedText != null && !transcribedText.isEmpty()) {
                userPrompt.append(transcribedText);
            }
            userPrompt.append("\n\n注意：本次评估无音视频数据，请仅基于文本内容评分，多模态分数字段返回默认值70。");

            String response = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            return parseOmniEvaluationResponse(response);

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

        String systemPrompt = promptTemplateService.getPrompt("followup-decision");

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("## 问题内容\n").append(question).append("\n\n");
        userPrompt.append("## 回答内容\n").append(answer).append("\n\n");
        userPrompt.append("## 评估结果\n");
        userPrompt.append("- 综合得分：").append(evaluation.getOverallScore()).append("\n");
        userPrompt.append("- 优点：").append(String.join(", ", evaluation.getStrengths())).append("\n");
        userPrompt.append("- 不足：").append(String.join(", ", evaluation.getWeaknesses())).append("\n\n");
        userPrompt.append("## 多模态分析\n");
        userPrompt.append("- 表情得分：").append(evaluation.getEmotionScore()).append("\n");
        userPrompt.append("- 肢体语言得分：").append(evaluation.getBodyLanguageScore()).append("\n");
        userPrompt.append("- 语音得分：").append(evaluation.getVoiceToneScore()).append("\n");
        if (evaluation.getModalityFollowUpSuggestion() != null && !evaluation.getModalityFollowUpSuggestion().isEmpty()) {
            userPrompt.append("- 多模态异常：").append(evaluation.getModalityFollowUpSuggestion()).append("\n");
        }
        userPrompt.append("\n## 追问次数\n");
        userPrompt.append("- 已追问次数：").append(followUpCount).append("\n");
        userPrompt.append("- 追问上限：").append(maxFollowUps).append("\n");

        try {
            String response = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            return parseFollowUpDecision(response);

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
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            // 提取多模态分数字段
            int emotionScore = json.getInt("emotionScore", 70);
            int bodyLanguageScore = json.getInt("bodyLanguageScore", 70);
            int voiceToneScore = json.getInt("voiceToneScore", 70);

            // 提取语义评分（根据问题类型不同字段名也不同，做兼容处理）
            int accuracy = json.getInt("accuracy",
                    json.getInt("businessAcumen",
                            json.getInt("situationClarity",
                                    json.getInt("communication", 70))));

            int logic = json.getInt("depthAndBreadth",
                    json.getInt("problemFraming",
                            json.getInt("taskClarity",
                                    json.getInt("collaboration", 70))));

            int fluency = json.getInt("understandingAndApplication",
                    json.getInt("analyticalFramework",
                            json.getInt("actionSpecificity",
                                    json.getInt("learnability", 70))));

            int confidence = json.getInt("clarityAndExpression",
                    json.getInt("solution",
                            json.getInt("resultQuantification",
                                    json.getInt("problemSolving", 70))));

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

    /**
     * 解析追问决策结果
     */
    private FollowUpDecisionBO parseFollowUpDecision(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            String decision = json.getStr("decision", "nextQuestion");

            return FollowUpDecisionBO.builder()
                    .decision(decision)
                    .followUpQuestion(json.getStr("followUpQuestion", ""))
                    .reason(json.getStr("reason", ""))
                    .followUpType(json.getStr("followUpType", ""))
                    .build();

        } catch (Exception e) {
            log.error("解析追问决策结果失败: {}", e.getMessage());
            return FollowUpDecisionBO.builder()
                    .decision("nextQuestion")
                    .reason("解析失败")
                    .build();
        }
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

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
