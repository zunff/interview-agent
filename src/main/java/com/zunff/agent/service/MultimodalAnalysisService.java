package com.zunff.agent.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.agent.model.bo.EvaluationBO;
import com.zunff.agent.model.dto.analysis.AudioAnalysisResult;
import com.zunff.agent.model.dto.analysis.VideoAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 多模态分析服务
 * 使用 Spring AI 调用通义千问 VL 和 Audio API 进行视频帧和音频分析
 */
@Slf4j
public class MultimodalAnalysisService {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    @Value("${interview.multimodal.vl-model:qwen-vl-max}")
    private String vlModel;

    @Value("${interview.multimodal.enabled:true}")
    private boolean multimodalEnabled;

    public MultimodalAnalysisService(ChatClient.Builder chatClientBuilder,
                                      PromptTemplateService promptTemplateService) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 分析视频帧
     * 使用 Spring AI 多模态能力调用通义千问 VL 模型
     *
     * @param base64Frames Base64编码的视频帧列表
     * @return 分析结果
     */
    public VideoAnalysisResult analyzeVideoFrames(List<String> base64Frames) {
        if (base64Frames == null || base64Frames.isEmpty()) {
            return VideoAnalysisResult.empty();
        }

        log.info("开始分析 {} 帧视频数据", base64Frames.size());

        if (!multimodalEnabled) {
            log.debug("多模态分析已禁用，返回默认结果");
            return VideoAnalysisResult.defaultResult();
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

            // 使用 Spring AI 的 ChatClient 发送多模态请求
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .user(userSpec -> {
                        userSpec.text(prompt);
                        userSpec.media(mediaList.toArray(new Media[0]));
                    })
                    .call()
                    .content();

            return parseVideoAnalysisResult(response);

        } catch (Exception e) {
            log.error("视频帧分析失败", e);
            return VideoAnalysisResult.defaultResult();
        }
    }

    /**
     * 分析音频
     * 使用 Spring AI 调用通义千问 Audio 模型
     *
     * @param audioBase64 Base64编码的音频数据
     * @return 分析结果
     */
    public AudioAnalysisResult analyzeAudio(String audioBase64) {
        if (audioBase64 == null || audioBase64.isEmpty()) {
            return AudioAnalysisResult.empty();
        }

        log.info("开始分析音频数据");

        if (!multimodalEnabled) {
            log.debug("多模态分析已禁用，返回默认结果");
            return AudioAnalysisResult.defaultResult();
        }

        try {
            // 从模板加载 prompt
            String prompt = promptTemplateService.getPrompt("audio-analysis");

            // 构建音频 Media 对象
            byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
            ByteArrayResource resource = new ByteArrayResource(audioBytes);
            Media audioMedia = new Media(MimeTypeUtils.parseMimeType("audio/wav"), resource);

            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .user(userSpec -> {
                        userSpec.text(prompt);
                        userSpec.media(audioMedia);
                    })
                    .call()
                    .content();

            return parseAudioAnalysisResult(response);

        } catch (Exception e) {
            log.error("音频分析失败", e);
            return AudioAnalysisResult.defaultResult();
        }
    }

    /**
     * 综合多模态评估
     * 结合文本回答、视频分析、音频分析进行综合评估
     */
    public EvaluationBO comprehensiveEvaluate(
            String question,
            String answerText,
            VideoAnalysisResult videoResult,
            AudioAnalysisResult audioResult) {

        log.info("开始综合评估");

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("evaluation");

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("问题：").append(question).append("\n\n");
        userPrompt.append("回答内容：").append(answerText).append("\n\n");
        userPrompt.append("视频分析结果：\n");
        userPrompt.append("- 表情得分：").append(videoResult.getEmotionScore()).append("\n");
        userPrompt.append("- 肢体语言得分：").append(videoResult.getBodyLanguageScore()).append("\n");
        userPrompt.append("- 表情分析：").append(videoResult.getEmotionAnalysis()).append("\n\n");
        userPrompt.append("音频分析结果：\n");
        userPrompt.append("- 语音得分：").append(audioResult.getVoiceToneScore()).append("\n");
        userPrompt.append("- 语音分析：").append(audioResult.getToneAnalysis()).append("\n");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            return parseEvaluationBO(response, videoResult, audioResult);

        } catch (Exception e) {
            log.error("综合评估失败", e);
            return EvaluationBO.builder()
                    .accuracy(60)
                    .logic(60)
                    .fluency(60)
                    .confidence(60)
                    .emotionScore(videoResult.getEmotionScore())
                    .bodyLanguageScore(videoResult.getBodyLanguageScore())
                    .voiceToneScore(audioResult.getVoiceToneScore())
                    .overallScore(60)
                    .needFollowUp(false)
                    .detailedEvaluation("评估过程中出现错误，使用默认评分")
                    .build();
        }
    }

    /**
     * 解析视频分析结果
     */
    private VideoAnalysisResult parseVideoAnalysisResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject result = JSONUtil.parseObj(jsonStr);

            return VideoAnalysisResult.builder()
                    .emotionScore(result.getInt("emotionScore", 70))
                    .bodyLanguageScore(result.getInt("bodyLanguageScore", 70))
                    .emotionAnalysis(result.getStr("emotionAnalysis", "分析完成"))
                    .bodyLanguageAnalysis(result.getStr("bodyLanguageAnalysis", "分析完成"))
                    .suggestions(parseStringList(result, "suggestions"))
                    .build();

        } catch (Exception e) {
            log.error("解析视频分析结果失败: {}", e.getMessage());
            return VideoAnalysisResult.defaultResult();
        }
    }

    /**
     * 解析音频分析结果
     */
    private AudioAnalysisResult parseAudioAnalysisResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject result = JSONUtil.parseObj(jsonStr);

            return AudioAnalysisResult.builder()
                    .voiceToneScore(result.getInt("voiceToneScore", 70))
                    .transcribedText(result.getStr("transcribedText", ""))
                    .toneAnalysis(result.getStr("toneAnalysis", "语音分析完成"))
                    .emotionAnalysis(result.getStr("emotionAnalysis", "情感分析完成"))
                    .suggestions(parseStringList(result, "suggestions"))
                    .build();

        } catch (Exception e) {
            log.error("解析音频分析结果失败: {}", e.getMessage());
            return AudioAnalysisResult.defaultResult();
        }
    }

    private EvaluationBO parseEvaluationBO(String response,
                                            VideoAnalysisResult videoResult,
                                            AudioAnalysisResult audioResult) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            return EvaluationBO.builder()
                    .accuracy(json.getInt("accuracy", 60))
                    .logic(json.getInt("logic", 60))
                    .fluency(json.getInt("fluency", 60))
                    .confidence(json.getInt("confidence", 60))
                    .emotionScore(videoResult.getEmotionScore())
                    .bodyLanguageScore(videoResult.getBodyLanguageScore())
                    .voiceToneScore(audioResult.getVoiceToneScore())
                    .overallScore(json.getInt("overallScore", 60))
                    .strengths(parseStringList(json, "strengths"))
                    .weaknesses(parseStringList(json, "weaknesses"))
                    .needFollowUp(json.getBool("needFollowUp", false))
                    .followUpSuggestion(json.getStr("followUpSuggestion", ""))
                    .detailedEvaluation(json.getStr("detailedEvaluation", ""))
                    .build();
        } catch (Exception e) {
            log.error("解析评估结果失败: {}", e.getMessage());
            return EvaluationBO.builder()
                    .accuracy(60)
                    .logic(60)
                    .fluency(60)
                    .confidence(60)
                    .emotionScore(videoResult.getEmotionScore())
                    .bodyLanguageScore(videoResult.getBodyLanguageScore())
                    .voiceToneScore(audioResult.getVoiceToneScore())
                    .overallScore(60)
                    .needFollowUp(false)
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
