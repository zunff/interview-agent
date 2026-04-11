package com.zunff.interview.service.extend;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.audio.transcription.AudioTranscriptionModel;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.analysis.AudioAnalysisResult;
import com.zunff.interview.model.dto.analysis.VisionAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 多模态分析服务
 * 使用 Spring AI 调用通义千问模型进行视频帧和音频分析
 *
 * 模型分工（独立 ChatClient Bean，避免 Builder 共享污染）：
 * - textChatClient (qwen-plus): 文本评估 + 语音情感分析
 * - visionChatClient (qwen3.5-omni-plus): 视频帧分析
 * - transcriptionModel (Spring AI Alibaba): 语音转录 ASR
 */
@Slf4j
public class MultimodalAnalysisService {

    private final ChatClient textChatClient;                  // 文本评估 + 情感分析
    private final ChatClient visionChatClient;                // 视觉分析
    private final AudioTranscriptionModel transcriptionModel; // 语音转录 (Spring AI Alibaba)
    private final PromptTemplateService promptTemplateService;
    private final boolean multimodalEnabled;

    public MultimodalAnalysisService(ChatClient textChatClient,
                                      ChatClient visionChatClient,
                                      AudioTranscriptionModel transcriptionModel,
                                      PromptTemplateService promptTemplateService,
                                      boolean multimodalEnabled) {
        this.textChatClient = textChatClient;
        this.visionChatClient = visionChatClient;
        this.transcriptionModel = transcriptionModel;
        this.promptTemplateService = promptTemplateService;
        this.multimodalEnabled = multimodalEnabled;
    }

    /**
     * 分析视频帧
     * 使用视觉模型 qwen-image-2.0-pro
     *
     * @param base64Frames Base64编码的视频帧列表
     * @return 分析结果
     */
    public VisionAnalysisResult analyzeVideoFrames(List<String> base64Frames) {
        if (base64Frames == null || base64Frames.isEmpty()) {
            log.info("视频帧数据为空，跳过视觉分析");
            return VisionAnalysisResult.empty();
        }

        log.info("开始分析 {} 帧视频数据，使用视觉模型", base64Frames.size());

        if (!multimodalEnabled) {
            log.debug("多模态分析已禁用，返回默认结果");
            return VisionAnalysisResult.defaultResult();
        }

        try {
            // 取前5帧进行分析
            List<String> framesToAnalyze = base64Frames.size() > 5
                    ? base64Frames.subList(0, 5)
                    : base64Frames;

            // 从模板加载 prompt
            String prompt = promptTemplateService.getPrompt("video-analysis");

            // 构建 Media 对象列表
            List<Media> mediaList = new ArrayList<>();
            for (String frame : framesToAnalyze) {
                byte[] imageBytes = Base64.getDecoder().decode(frame);
                ByteArrayResource resource = new ByteArrayResource(imageBytes);
                mediaList.add(new Media(MimeTypeUtils.IMAGE_JPEG, resource));
            }

            // 使用视觉模型 ChatClient（独立 Bean，不会污染文本模型）
            String response = visionChatClient.prompt()
                    .user(userSpec -> {
                        userSpec.text(prompt);
                        userSpec.media(mediaList.toArray(new Media[0]));
                    })
                    .call()
                    .content();

            return parseVisionAnalysisResult(response);

        } catch (Exception e) {
            throw new RuntimeException("视频帧分析失败", e);
        }
    }

    /**
     * 分析音频
     * 步骤1: 使用 Spring AI AudioTranscriptionModel 转录语音
     * 步骤2: 使用文本模型分析情感语调
     *
     * @param audioBase64 Base64编码的音频数据
     * @return 分析结果
     */
    public AudioAnalysisResult analyzeAudio(String audioBase64) {
        if (audioBase64 == null || audioBase64.isEmpty()) {
            log.info("音频数据为空，跳过ASR转录");
            return AudioAnalysisResult.empty();
        }

        log.info("开始分析音频数据，音频Base64长度: {}", audioBase64.length());

        if (!multimodalEnabled) {
            log.debug("多模态分析已禁用，返回默认结果");
            return AudioAnalysisResult.defaultResult();
        }

        try {
            // Step 1: 使用 Spring AI AudioTranscriptionModel 转录
            byte[] audioData = Base64.getDecoder().decode(audioBase64);
            ByteArrayResource audioResource = new ByteArrayResource(audioData);
            log.info("ASR转录请求：音频数据大小={} bytes，开始调用转录模型", audioData.length);

            AudioTranscriptionResponse response = transcriptionModel.call(
                    new AudioTranscriptionPrompt(audioResource));

            String transcribedText = response.getResult().getOutput();
            log.info("ASR转录完成，文本长度: {}，转录内容: {}", transcribedText != null ? transcribedText.length() : 0,
                    transcribedText != null ? transcribedText.substring(0, Math.min(100, transcribedText.length())) : "null");

            // Step 2: 使用文本模型分析情感语调
            return analyzeAudioEmotion(transcribedText);

        } catch (Exception e) {
            throw new RuntimeException("音频分析失败", e);
        }
    }

    /**
     * 使用文本模型分析转录文本的情感语调
     *
     * @param transcribedText 转录文本
     * @return 音频分析结果
     */
    private AudioAnalysisResult analyzeAudioEmotion(String transcribedText) {
        if (transcribedText == null || transcribedText.isEmpty()) {
            return AudioAnalysisResult.defaultResult();
        }

        try {
            // 从模板加载情感分析 prompt
            String prompt = promptTemplateService.getPrompt("audio-emotion-analysis",
                    Map.of("transcribedText", transcribedText));

            // 直接使用文本模型 ChatClient（独立 Bean，模型已固定为 qwen-plus）
            String response = textChatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseAudioAnalysisResult(response, transcribedText);

        } catch (Exception e) {
            throw new RuntimeException("音频情感分析失败", e);
        }
    }

    /**
     * 综合多模态评估
     * 结合文本回答、视频分析、音频分析进行综合评估
     */
    public EvaluationBO comprehensiveEvaluate(
            String question,
            String answerText,
            VisionAnalysisResult visionResult,
            AudioAnalysisResult audioResult) {
        return comprehensiveEvaluate(question, answerText, visionResult, audioResult, "evaluation");
    }

    /**
     * 综合多模态评估（支持自定义评估模板）
     * 结合文本回答、视频分析、音频分析进行综合评估
     * @param evaluationPromptName 评估 Prompt 模板名称
     */
    public EvaluationBO comprehensiveEvaluate(
            String question,
            String answerText,
            VisionAnalysisResult visionResult,
            AudioAnalysisResult audioResult,
            String evaluationPromptName) {

        log.info("开始综合评估，使用模板: {}", evaluationPromptName);

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt(evaluationPromptName);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("问题：").append(question).append("\n\n");
        userPrompt.append("回答内容：").append(answerText).append("\n\n");
        userPrompt.append("视频分析结果：\n");
        userPrompt.append("- 表情得分：").append(visionResult.getEmotionScore()).append("\n");
        userPrompt.append("- 肢体语言得分：").append(visionResult.getBodyLanguageScore()).append("\n");
        userPrompt.append("- 表情分析：").append(visionResult.getEmotionAnalysis()).append("\n\n");
        userPrompt.append("音频分析结果：\n");
        userPrompt.append("- 语音得分：").append(audioResult.getVoiceToneScore()).append("\n");
        userPrompt.append("- 语音分析：").append(audioResult.getToneAnalysis()).append("\n");

        try {
            // 直接使用文本模型 ChatClient（独立 Bean，模型已固定为 qwen-plus）
            String response = textChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            return parseEvaluationBO(response, visionResult, audioResult);

        } catch (Exception e) {
            throw new RuntimeException("综合评估失败", e);
        }
    }

    /**
     * 解析视频分析结果
     */
    private VisionAnalysisResult parseVisionAnalysisResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject result = JSONUtil.parseObj(jsonStr);

            int emotionScore = result.getInt("emotionScore", 70);
            int bodyLanguageScore = result.getInt("bodyLanguageScore", 70);
            String emotionAnalysis = result.getStr("emotionAnalysis", "分析完成");
            String bodyLanguageAnalysis = result.getStr("bodyLanguageAnalysis", "分析完成");

            // 检测多模态异常
            boolean hasConcern = emotionScore < 60 || bodyLanguageScore < 60;
            String followUpSuggestion = generateVideoFollowUpSuggestion(emotionScore, bodyLanguageScore, emotionAnalysis, bodyLanguageAnalysis);

            return VisionAnalysisResult.builder()
                    .emotionScore(emotionScore)
                    .bodyLanguageScore(bodyLanguageScore)
                    .emotionAnalysis(emotionAnalysis)
                    .bodyLanguageAnalysis(bodyLanguageAnalysis)
                    .suggestions(parseStringList(result, "suggestions"))
                    .followUpSuggestion(followUpSuggestion)
                    .hasConcern(hasConcern)
                    .build();

        } catch (Exception e) {
            log.error("解析视频分析结果失败: {}", e.getMessage());
            return VisionAnalysisResult.defaultResult();
        }
    }

    /**
     * 根据视频分析结果生成追问建议
     */
    private String generateVideoFollowUpSuggestion(int emotionScore, int bodyLanguageScore,
                                                    String emotionAnalysis, String bodyLanguageAnalysis) {
        StringBuilder suggestion = new StringBuilder();

        if (emotionScore < 60) {
            suggestion.append("表情得分较低(").append(emotionScore).append(")，").append(emotionAnalysis);
            suggestion.append("，建议追问候选人是否对当前话题有顾虑或压力");
        }

        if (bodyLanguageScore < 60) {
            if (suggestion.length() > 0) suggestion.append("；");
            suggestion.append("肢体语言得分较低(").append(bodyLanguageScore).append(")，").append(bodyLanguageAnalysis);
            suggestion.append("，建议追问候选人的自信程度或沟通风格");
        }

        return suggestion.length() > 0 ? suggestion.toString() : "";
    }

    /**
     * 解析音频分析结果
     */
    private AudioAnalysisResult parseAudioAnalysisResult(String response, String transcribedText) {
        try {
            String jsonStr = extractJson(response);
            JSONObject result = JSONUtil.parseObj(jsonStr);

            int voiceToneScore = result.getInt("voiceToneScore", 70);
            String toneAnalysis = result.getStr("toneAnalysis", "语音分析完成");
            String emotionAnalysis = result.getStr("emotionAnalysis", "情感分析完成");

            // 检测多模态异常
            boolean hasConcern = voiceToneScore < 60;
            String followUpSuggestion = generateAudioFollowUpSuggestion(voiceToneScore, toneAnalysis, emotionAnalysis);

            return AudioAnalysisResult.builder()
                    .voiceToneScore(voiceToneScore)
                    .transcribedText(transcribedText)
                    .toneAnalysis(toneAnalysis)
                    .emotionAnalysis(emotionAnalysis)
                    .suggestions(parseStringList(result, "suggestions"))
                    .followUpSuggestion(followUpSuggestion)
                    .hasConcern(hasConcern)
                    .build();

        } catch (Exception e) {
            log.error("解析音频分析结果失败: {}", e.getMessage());
            return AudioAnalysisResult.defaultResult();
        }
    }

    /**
     * 根据音频分析结果生成追问建议
     */
    private String generateAudioFollowUpSuggestion(int voiceToneScore, String toneAnalysis, String emotionAnalysis) {
        if (voiceToneScore < 60) {
            return "语音语调得分较低(" + voiceToneScore + ")，" + toneAnalysis +
                    "，建议追问候选人是否紧张或有表达困难";
        }
        return "";
    }

    private EvaluationBO parseEvaluationBO(String response,
                                            VisionAnalysisResult visionResult,
                                            AudioAnalysisResult audioResult) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            // 生成多模态追问建议
            String modalityFollowUpSuggestion = buildModalityFollowUpSuggestion(visionResult, audioResult);
            boolean modalityConcern = visionResult.isHasConcern() || audioResult.isHasConcern();

            return EvaluationBO.builder()
                    .accuracy(json.getInt("accuracy", 60))
                    .logic(json.getInt("logic", 60))
                    .fluency(json.getInt("fluency", 60))
                    .confidence(json.getInt("confidence", 60))
                    .emotionScore(visionResult.getEmotionScore())
                    .bodyLanguageScore(visionResult.getBodyLanguageScore())
                    .voiceToneScore(audioResult.getVoiceToneScore())
                    .overallScore(json.getInt("overallScore", 60))
                    .strengths(parseStringList(json, "strengths"))
                    .weaknesses(parseStringList(json, "weaknesses"))
                    .needFollowUp(json.getBool("needFollowUp", false))
                    .followUpSuggestion(json.getStr("followUpSuggestion", ""))
                    .detailedEvaluation(json.getStr("detailedEvaluation", ""))
                    .modalityFollowUpSuggestion(modalityFollowUpSuggestion)
                    .modalityConcern(modalityConcern)
                    .build();
        } catch (Exception e) {
            log.error("解析评估结果失败: {}", e.getMessage());
            return EvaluationBO.builder()
                    .accuracy(60)
                    .logic(60)
                    .fluency(60)
                    .confidence(60)
                    .emotionScore(visionResult.getEmotionScore())
                    .bodyLanguageScore(visionResult.getBodyLanguageScore())
                    .voiceToneScore(audioResult.getVoiceToneScore())
                    .overallScore(60)
                    .needFollowUp(false)
                    .build();
        }
    }

    /**
     * 构建多模态追问建议
     */
    private String buildModalityFollowUpSuggestion(VisionAnalysisResult visionResult, AudioAnalysisResult audioResult) {
        StringBuilder suggestion = new StringBuilder();

        if (visionResult.getFollowUpSuggestion() != null && !visionResult.getFollowUpSuggestion().isEmpty()) {
            suggestion.append(visionResult.getFollowUpSuggestion());
        }

        if (audioResult.getFollowUpSuggestion() != null && !audioResult.getFollowUpSuggestion().isEmpty()) {
            if (suggestion.length() > 0) suggestion.append("；");
            suggestion.append(audioResult.getFollowUpSuggestion());
        }

        return suggestion.toString();
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
