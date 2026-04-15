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
     * 多模态综合分析（视频帧 + 音频 + 文本）
     * 使用 system + user 双消息，system 负责约束输出与评估规则。
     *
     * @param base64Frames   Base64 编码的视频帧列表
     * @param base64WavAudio Base64 编码的 WAV 音频数据
     * @param systemPrompt   系统级提示词
     * @param textPrompt     用户文本提示
     * @return 分析结果文本
     */
    public String analyzeMultimodal(
            List<String> base64Frames,
            String base64WavAudio,
            String systemPrompt,
            String textPrompt) {
        log.info("开始多模态综合分析，视频帧: {}, 音频: {}",
                base64Frames != null ? base64Frames.size() : 0,
                base64WavAudio != null ? "有" : "无");

        try {
            List<Object> content = new ArrayList<>();

            // 1. 视频帧（type: "video", video: [base64 images]）
            if (base64Frames != null && !base64Frames.isEmpty()) {
                JSONArray videoFrames = new JSONArray();
                for (String frame : base64Frames) {
                    videoFrames.put("data:image/jpeg;base64," + frame);
                }
                JSONObject videoContent = new JSONObject();
                videoContent.set("type", "video");
                videoContent.set("video", videoFrames);
                content.add(videoContent);
            }

            // 2. 音频（type: "input_audio", input_audio: {data, format:"wav"}）
            if (base64WavAudio != null && !base64WavAudio.isEmpty()) {
                JSONObject inputAudio = new JSONObject();
                inputAudio.set("data", "data:;base64," + base64WavAudio);
                inputAudio.set("format", "wav");

                JSONObject audioContent = new JSONObject();
                audioContent.set("type", "input_audio");
                audioContent.set("input_audio", inputAudio);
                content.add(audioContent);
            }

            // 3. 文本提示
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", textPrompt);
            content.add(textContent);

            // 构建请求（system + user）
            JSONObject systemMessage = new JSONObject();
            systemMessage.set("role", "system");
            systemMessage.set("content", systemPrompt);

            JSONObject userMessage = new JSONObject();
            userMessage.set("role", "user");
            userMessage.set("content", content);

            return sendRequest(List.of(systemMessage, userMessage));

        } catch (Exception e) {
            log.error("多模态综合分析失败", e);
            throw new RuntimeException("多模态综合分析失败", e);
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
        log.debug("请求体: {}", requestBody);

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
