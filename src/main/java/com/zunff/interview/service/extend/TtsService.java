package com.zunff.interview.service.extend;

import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.zunff.interview.config.TtsConfig;
import com.zunff.interview.model.websocket.WebSocketMessage;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * TTS 语音合成服务
 * 使用 DashScope Qwen-TTS 模型流式合成语音，通过 WebSocket 推送给前端
 */
@Slf4j
@Service
public class TtsService {

    private final String apiKey;
    private final String model;
    private final String voice;
    private final boolean enabled;

    public TtsService(TtsConfig ttsConfig) {
        this.apiKey = ttsConfig.getApiKey();
        this.model = ttsConfig.getModel();
        this.voice = ttsConfig.getVoice();
        this.enabled = ttsConfig.isEnabled();
        log.info("TtsService 初始化: model={}, voice={}, enabled={}", model, voice, enabled);
    }

    /**
     * 流式合成语音并通过 WebSocket 推送
     *
     * @param text      要合成的文本
     * @param sessionId 面试会话ID
     * @param session   WebSocket 会话
     */
    public void synthesizeAndStream(String text, String sessionId, WebSocketSession session) {
        if (!enabled || text == null || text.isEmpty()) {
            log.debug("TTS 未启用或文本为空，跳过语音合成");
            return;
        }

        try {
            // 1. 发送 audio_question_start
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_START,
                    Map.of("format", "wav")));

            // 2. 构建参数并流式调用
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model(model)
                    .apiKey(apiKey)
                    .text(text)
                    .voice(AudioParameters.Voice.valueOf(voice.toUpperCase()))
                    .build();

            Flowable<MultiModalConversationResult> flowable = conv.streamCall(param);

            flowable.blockingForEach(result -> {
                // 检查线程是否被中断（会话关闭或用户打断）
                if (Thread.currentThread().isInterrupted()) {
                    log.info("TTS 线程被中断，提前结束，sessionId: {}", sessionId);
                    throw new InterruptedException("TTS interrupted");
                }
                String audioBase64 = extractAudioData(result);
                if (audioBase64 != null && !audioBase64.isEmpty()) {
                    sendWsMessage(session, WebSocketMessage.of(
                            WebSocketMessage.Type.AUDIO_QUESTION_CHUNK,
                            Map.of("data", audioBase64)));
                }
            });

            // 3. 发送 audio_question_end
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_END,
                    Map.of("sessionId", sessionId)));

            log.info("TTS 合成完成，sessionId: {}", sessionId);

        } catch (Exception e) {
            log.error("TTS 合成失败，降级为纯文字，sessionId: {}", sessionId, e);
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_ERROR,
                    Map.of("message", "语音合成失败")));
        }
    }

    /**
     * 从 MultiModalConversationResult 中提取 Base64 音频数据
     */
    private String extractAudioData(MultiModalConversationResult result) {
        try {
            if (result.getOutput() != null
                    && result.getOutput().getAudio() != null
                    && result.getOutput().getAudio().getData() != null) {
                return result.getOutput().getAudio().getData();
            }
        } catch (Exception e) {
            log.warn("提取音频数据异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 通过 WebSocket 发送 JSON 文本消息
     */
    private void sendWsMessage(WebSocketSession session, WebSocketMessage<?> message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = cn.hutool.json.JSONUtil.toJsonStr(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("WebSocket 发送 TTS 消息失败", e);
        }
    }
}
