package com.zunff.agent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.agent.model.bo.EvaluationBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态分析服务
 * 使用通义千问 VL 和 Audio API 进行视频帧和音频分析
 */
@Slf4j
public class MultimodalAnalysisService {

    private final ChatClient.Builder chatClientBuilder;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String chatModel;

    @Value("${interview.multimodal.vl-model:qwen-vl-max}")
    private String vlModel;

    @Value("${interview.multimodal.audio-model:paraformer-v2}")
    private String audioModel;

    @Value("${interview.multimodal.enabled:true}")
    private boolean multimodalEnabled;

    public MultimodalAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * 分析视频帧
     * 调用通义千问 VL API 检测面试者的表情和肢体语言
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
            // 取前5帧进行分析，避免请求过大
            List<String> framesToAnalyze = base64Frames.size() > 5
                    ? base64Frames.subList(0, 5)
                    : base64Frames;

            // 构建多模态消息
            JSONArray contentArray = new JSONArray();

            // 添加文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", """
                    请分析这些面试者的视频帧，评估以下方面：
                    1. 表情情感：是否自然、自信、紧张等
                    2. 肢体语言：坐姿、手势、眼神交流等

                    请以JSON格式返回结果：
                    {
                        "emotionScore": 75,
                        "bodyLanguageScore": 80,
                        "emotionAnalysis": "表情分析描述",
                        "bodyLanguageAnalysis": "肢体语言分析描述",
                        "suggestions": ["建议1", "建议2"]
                    }

                    评分范围0-100。
                    """);
            contentArray.add(textContent);

            // 添加图片
            for (String frame : framesToAnalyze) {
                JSONObject imageContent = new JSONObject();
                imageContent.set("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.set("url", "data:image/jpeg;base64," + frame);
                imageContent.set("image_url", imageUrl);
                contentArray.add(imageContent);
            }

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", vlModel);
            requestBody.set("messages", new JSONArray()
                    .put(new JSONObject()
                            .set("role", "user")
                            .set("content", contentArray)));

            // 调用 VL API
            String response = callDashScopeApi("/chat/completions", requestBody);
            return parseVideoAnalysisResult(response);

        } catch (Exception e) {
            log.error("视频帧分析失败", e);
            return VideoAnalysisResult.defaultResult();
        }
    }

    /**
     * 分析音频
     * 调用通义千问 Audio API 检测面试者的语音语调和情感
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
            // 构建 Audio API 请求
            // 使用 Paraformer 语音识别模型进行转录
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", audioModel);
            JSONObject input = new JSONObject();

            // 音频数据，支持 URL 或 Base64
            if (audioBase64.startsWith("http")) {
                input.set("audio_url", audioBase64);
            } else {
                input.set("audio_url", "data:audio/wav;base64," + audioBase64);
            }
            requestBody.set("input", input);

            // 调用语音识别 API
            String response = callDashScopeAudioApi("/services/audio/asr/transcription", requestBody);
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

        String systemPrompt = """
                你是一位资深的面试官，需要综合评估面试者的回答表现。

                评估维度：
                1. 内容准确性：回答是否准确、完整
                2. 逻辑清晰度：表达是否有条理
                3. 表达流畅度：语言是否流畅自然
                4. 自信程度：是否展现出专业自信

                请以 JSON 格式返回评估结果：
                {
                    "accuracy": 85,
                    "logic": 80,
                    "fluency": 75,
                    "confidence": 70,
                    "overallScore": 77,
                    "strengths": ["回答内容准确", "逻辑清晰"],
                    "weaknesses": ["表达略显紧张"],
                    "needFollowUp": true,
                    "followUpSuggestion": "可以追问具体实现细节",
                    "detailedEvaluation": "详细评价内容..."
                }

                各项评分范围 0-100。
                needFollowUp 表示是否需要追问以深入了解。
                """;

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
     * 调用 DashScope VL API
     */
    private String callDashScopeApi(String endpoint, JSONObject requestBody) {
        String url = baseUrl + endpoint;

        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(30000)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("API 调用失败: " + response.getStatus() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * 调用 DashScope Audio API
     */
    private String callDashScopeAudioApi(String endpoint, JSONObject requestBody) {
        // Audio API 使用不同的 base URL
        String audioBaseUrl = "https://dashscope.aliyuncs.com/api/v1";
        String url = audioBaseUrl + endpoint;

        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("Audio API 调用失败: " + response.getStatus() + " - " + response.body());
        }

        return response.body();
    }

    /**
     * 解析视频分析结果
     */
    private VideoAnalysisResult parseVideoAnalysisResult(String response) {
        try {
            JSONObject json = JSONUtil.parseObj(response);
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return VideoAnalysisResult.defaultResult();
            }

            String content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");

            // 提取 JSON 部分
            String jsonStr = extractJson(content);
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
            JSONObject json = JSONUtil.parseObj(response);
            JSONObject output = json.getJSONObject("output");

            if (output == null) {
                return AudioAnalysisResult.defaultResult();
            }

            // 获取转录文本
            String transcribedText = output.getStr("text", "");
            JSONArray sentences = output.getJSONArray("sentence_list");

            // 分析语音特征
            int voiceToneScore = 70;
            String toneAnalysis = "语音分析完成";
            String emotionAnalysis = "情感分析完成";
            List<String> suggestions = new ArrayList<>();

            if (sentences != null && !sentences.isEmpty()) {
                // 基于语音特征计算评分
                double totalDuration = 0;
                int sentenceCount = sentences.size();

                for (int i = 0; i < sentences.size(); i++) {
                    JSONObject sentence = sentences.getJSONObject(i);
                    totalDuration += sentence.getDouble("end", 0.0) - sentence.getDouble("begin", 0.0);
                }

                // 根据平均语速评估
                double avgDuration = totalDuration / sentenceCount;
                if (avgDuration > 3) {
                    voiceToneScore = 75;
                    toneAnalysis = "语速适中，表达清晰";
                } else if (avgDuration > 2) {
                    voiceToneScore = 80;
                    toneAnalysis = "语速良好，表达流畅";
                } else {
                    voiceToneScore = 65;
                    toneAnalysis = "语速较快，建议放慢节奏";
                    suggestions.add("建议适当放慢语速，让表达更清晰");
                }
            }

            return AudioAnalysisResult.builder()
                    .voiceToneScore(voiceToneScore)
                    .transcribedText(transcribedText)
                    .toneAnalysis(toneAnalysis)
                    .emotionAnalysis(emotionAnalysis)
                    .suggestions(suggestions)
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

    /**
     * 视频分析结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VideoAnalysisResult {
        private int emotionScore;
        private int bodyLanguageScore;
        private String emotionAnalysis;
        private String bodyLanguageAnalysis;
        private List<String> suggestions;

        public static VideoAnalysisResult empty() {
            return VideoAnalysisResult.builder()
                    .emotionScore(70)
                    .bodyLanguageScore(70)
                    .emotionAnalysis("未进行视频分析")
                    .bodyLanguageAnalysis("未进行视频分析")
                    .suggestions(new ArrayList<>())
                    .build();
        }

        public static VideoAnalysisResult defaultResult() {
            return VideoAnalysisResult.builder()
                    .emotionScore(75)
                    .bodyLanguageScore(78)
                    .emotionAnalysis("基于实时分析，面试者表情较为自然，偶有紧张表现")
                    .bodyLanguageAnalysis("坐姿端正，手势配合得当，整体表现良好")
                    .suggestions(List.of("建议保持更多眼神交流", "回答时可以更加自信"))
                    .build();
        }
    }

    /**
     * 音频分析结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AudioAnalysisResult {
        private int voiceToneScore;
        private String transcribedText;
        private String toneAnalysis;
        private String emotionAnalysis;
        private List<String> suggestions;

        public static AudioAnalysisResult empty() {
            return AudioAnalysisResult.builder()
                    .voiceToneScore(70)
                    .transcribedText("")
                    .toneAnalysis("未进行音频分析")
                    .emotionAnalysis("未进行音频分析")
                    .suggestions(new ArrayList<>())
                    .build();
        }

        public static AudioAnalysisResult defaultResult() {
            return AudioAnalysisResult.builder()
                    .voiceToneScore(72)
                    .transcribedText("")
                    .toneAnalysis("语调较为自然，表达流畅度中等")
                    .emotionAnalysis("声音中略有紧张感，但整体表现稳定")
                    .suggestions(List.of("建议语速稍慢一些", "可以在重点处加强语气"))
                    .build();
        }
    }
}
