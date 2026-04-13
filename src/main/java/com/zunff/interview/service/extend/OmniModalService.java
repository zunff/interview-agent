package com.zunff.interview.service.extend;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Qwen-Omni 多模态服务
 * 使用 DashScope OpenAI 兼容 API 调用 qwen3.5-omni-plus 模型
 *
 * 支持的输入模态：
 * - 图片 (image_url)
 * - 音频 (input_audio)
 * - 视频 (video_url 或图片列表)
 */
@Slf4j
public class OmniModalService {

    private static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";

    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public OmniModalService(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = StrUtil.removeSuffix(baseUrl, "/");
        log.info("QwenOmniService 初始化: model={}, baseUrl={}", model, this.baseUrl);
    }

    /**
     * 分析多张图片（作为视频帧序列）
     *
     * @param base64Frames Base64 编码的图片列表
     * @param prompt 提示词
     * @return 分析结果文本
     */
    public String analyzeImages(List<String> base64Frames, String prompt) {
        if (base64Frames == null || base64Frames.isEmpty()) {
            log.warn("图片列表为空，跳过分析");
            return "";
        }

        log.info("开始分析 {} 张图片", base64Frames.size());

        try {
            // 构建消息内容
            List<Object> content = new ArrayList<>();

            // 添加图片（使用 video 类型传入图片列表，模拟视频帧）
            // 根据 Qwen-Omni 文档，可以使用 type: "video" 传入图片列表
            JSONArray videoFrames = new JSONArray();
            for (String frame : base64Frames) {
                videoFrames.put("data:image/jpeg;base64," + frame);
            }

            JSONObject videoContent = new JSONObject();
            videoContent.set("type", "video");
            videoContent.set("video", videoFrames);
            content.add(videoContent);

            // 添加文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", prompt);
            content.add(textContent);

            // 构建请求
            JSONObject message = new JSONObject();
            message.set("role", "user");
            message.set("content", content);

            return sendRequest(List.of(message));

        } catch (Exception e) {
            log.error("图片分析失败", e);
            throw new RuntimeException("图片分析失败", e);
        }
    }

    /**
     * 分析单张图片
     *
     * @param base64Image Base64 编码的图片
     * @param prompt 提示词
     * @return 分析结果文本
     */
    public String analyzeImage(String base64Image, String prompt) {
        if (StrUtil.isBlank(base64Image)) {
            log.warn("图片为空，跳过分析");
            return "";
        }

        log.info("开始分析单张图片");

        try {
            List<Object> content = new ArrayList<>();

            // 添加图片
            JSONObject imageUrl = new JSONObject();
            imageUrl.set("url", "data:image/jpeg;base64," + base64Image);

            JSONObject imageContent = new JSONObject();
            imageContent.set("type", "image_url");
            imageContent.set("image_url", imageUrl);
            content.add(imageContent);

            // 添加文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", prompt);
            content.add(textContent);

            // 构建请求
            JSONObject message = new JSONObject();
            message.set("role", "user");
            message.set("content", content);

            return sendRequest(List.of(message));

        } catch (Exception e) {
            log.error("图片分析失败", e);
            throw new RuntimeException("图片分析失败", e);
        }
    }

    /**
     * 分析音频
     *
     * @param base64Audio Base64 编码的音频数据（原始音频，非 data URL 前缀）
     * @param format 音频格式 (wav, mp3, etc.)
     * @param prompt 提示词
     * @return 分析结果文本
     */
    public String analyzeAudio(String base64Audio, String format, String prompt) {
        if (StrUtil.isBlank(base64Audio)) {
            log.warn("音频为空，跳过分析");
            return "";
        }

        log.info("开始分析音频，格式: {}", format);

        try {
            List<Object> content = new ArrayList<>();

            // 添加音频
            JSONObject inputAudio = new JSONObject();
            inputAudio.set("data", "data:;base64," + base64Audio);
            inputAudio.set("format", format);

            JSONObject audioContent = new JSONObject();
            audioContent.set("type", "input_audio");
            audioContent.set("input_audio", inputAudio);
            content.add(audioContent);

            // 添加文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", prompt);
            content.add(textContent);

            // 构建请求
            JSONObject message = new JSONObject();
            message.set("role", "user");
            message.set("content", content);

            return sendRequest(List.of(message));

        } catch (Exception e) {
            log.error("音频分析失败", e);
            throw new RuntimeException("音频分析失败", e);
        }
    }

    /**
     * 分析视频 URL
     *
     * @param videoUrl 视频 URL
     * @param prompt 提示词
     * @return 分析结果文本
     */
    public String analyzeVideoUrl(String videoUrl, String prompt) {
        if (StrUtil.isBlank(videoUrl)) {
            log.warn("视频 URL 为空，跳过分析");
            return "";
        }

        log.info("开始分析视频 URL: {}", videoUrl);

        try {
            List<Object> content = new ArrayList<>();

            // 添加视频
            JSONObject videoUrlObj = new JSONObject();
            videoUrlObj.set("url", videoUrl);

            JSONObject videoContent = new JSONObject();
            videoContent.set("type", "video_url");
            videoContent.set("video_url", videoUrlObj);
            content.add(videoContent);

            // 添加文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", prompt);
            content.add(textContent);

            // 构建请求
            JSONObject message = new JSONObject();
            message.set("role", "user");
            message.set("content", content);

            return sendRequest(List.of(message));

        } catch (Exception e) {
            log.error("视频分析失败", e);
            throw new RuntimeException("视频分析失败", e);
        }
    }

    /**
     * 发送请求到 DashScope OpenAI 兼容 API
     * Qwen-Omni 必须使用流式输出 (stream=true)
     */
    private String sendRequest(List<JSONObject> messages) {
        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("messages", messages);
        requestBody.set("stream", true);
        requestBody.set("stream_options", JSONUtil.parseObj("{\"include_usage\": true}"));

        // 设置输出模态（仅文本）
        requestBody.set("modalities", JSONUtil.parseArray("[\"text\"]"));

        String url = baseUrl + CHAT_COMPLETIONS_ENDPOINT;
        log.debug("发送请求到: {}", url);
        log.debug("请求体: {}", requestBody.toString());

        try {
            HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody.toString())
                    .execute();

            if (!response.isOk()) {
                log.error("API 请求失败: status={}, body={}", response.getStatus(), response.body());
                throw new RuntimeException("API 请求失败: " + response.getStatus());
            }

            // 解析 SSE 流式响应
            return parseStreamResponse(response.body());

        } catch (Exception e) {
            log.error("发送 API 请求失败", e);
            throw new RuntimeException("发送 API 请求失败", e);
        }
    }

    /**
     * 解析 SSE 流式响应
     * 格式：data: {...}\n\ndata: {...}\n\n...
     */
    private String parseStreamResponse(String responseBody) {
        StringBuilder result = new StringBuilder();

        String[] lines = responseBody.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if ("[DONE]".equals(data)) {
                    continue;
                }

                try {
                    JSONObject chunk = JSONUtil.parseObj(data);
                    JSONArray choices = chunk.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.getJSONObject("delta");
                        if (delta != null && delta.containsKey("content")) {
                            String content = delta.getStr("content");
                            if (content != null) {
                                result.append(content);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("解析响应块失败: {}", data);
                }
            }
        }

        log.info("流式响应解析完成，结果长度: {}", result.length());
        return result.toString();
    }
}
