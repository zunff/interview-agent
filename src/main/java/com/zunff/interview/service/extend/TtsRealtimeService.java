package com.zunff.interview.service.extend;

import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig;
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam;
import com.google.gson.JsonObject;
import com.zunff.interview.config.TtsConfig;
import com.zunff.interview.model.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * TTS 语音合成服务
 * 使用 DashScope Qwen-TTS-Realtime 模型（WebSocket 流式合成），通过 WebSocket 推送给前端
 * 音频格式：Opus，通过 BinaryMessage 发送原始音频数据
 */
@Slf4j
public class TtsRealtimeService {

    private final String apiKey;
    private final String model;
    private final String voice;
    private final String url;
    private final boolean enabled;

    public TtsRealtimeService(String apiKey, TtsConfig ttsConfig) {
        this.apiKey = apiKey;
        this.model = ttsConfig.getModel();
        this.voice = ttsConfig.getVoice();
        this.url = ttsConfig.getUrl();
        this.enabled = ttsConfig.isEnabled();
        log.info("TtsService 初始化: model={}, voice={}, url={}, enabled={}", model, voice, url, enabled);
    }

    /**
     * 使用 Realtime API（commit 模式）流式合成语音并通过 WebSocket 推送
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

        QwenTtsRealtime qwenTts = null;
        try {
            // 1. 发送 audio_question_start
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_START,
                    Map.of("format", "opus")));

            // 2. 构建参数
            QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                    .model(model)
                    .url(url)
                    .apikey(apiKey)
                    .build();

            CountDownLatch finishLatch = new CountDownLatch(1);

            // 3. 创建 Realtime TTS 客户端，注册回调
            qwenTts = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("TTS Realtime 连接已建立, sessionId: {}", sessionId);
                }

                @Override
                public void onEvent(JsonObject message) {
                    String type = message.get("type").getAsString();
                    switch (type) {
                        case "session.created" -> {
                            String ttsSessionId = message.has("session")
                                    ? message.get("session").getAsJsonObject().get("id").getAsString()
                                    : "unknown";
                            log.debug("TTS Realtime 会话创建: {}", ttsSessionId);
                        }
                        case "response.audio.delta" -> {
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                            String audioBase64 = message.get("delta").getAsString();
                            if (audioBase64 != null && !audioBase64.isEmpty()) {
                                byte[] audioBytes = Base64.getDecoder().decode(audioBase64);
                                sendBinaryMessage(session, audioBytes);
                            }
                        }
                        case "response.done" -> log.debug("TTS Realtime 响应完成, sessionId: {}", sessionId);
                        case "session.finished" -> {
                            log.debug("TTS Realtime 会话结束, sessionId: {}", sessionId);
                            finishLatch.countDown();
                        }
                        default -> { /* 忽略其他事件 */ }
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("TTS Realtime 连接关闭: code={}, reason={}, sessionId: {}", code, reason, sessionId);
                    finishLatch.countDown();
                }
            });

            // 4. 连接并配置会话（Opus 格式）
            qwenTts.connect();
            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice(voice)
                    .format("opus")
                    .sampleRate(24000)
                    .mode("commit")
                    .build();
            qwenTts.updateSession(config);

            // 5. commit 模式：追加文本 → 提交 → 结束
            qwenTts.appendText(text);
            qwenTts.commit();
            qwenTts.finish();

            // 6. 等待合成完成
            finishLatch.await();

            // 7. 发送 audio_question_end
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_END,
                    Map.of("sessionId", sessionId)));

            log.info("TTS Realtime 合成完成, sessionId: {}", sessionId);

        } catch (InterruptedException e) {
            log.info("TTS 线程被中断，提前结束, sessionId: {}", sessionId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("TTS Realtime 合成失败，降级为纯文字, sessionId: {}", sessionId, e);
            sendWsMessage(session, WebSocketMessage.of(
                    WebSocketMessage.Type.AUDIO_QUESTION_ERROR,
                    Map.of("message", "语音合成失败")));
        } finally {
            if (qwenTts != null) {
                try {
                    qwenTts.close();
                } catch (Exception e) {
                    log.warn("关闭 TTS Realtime 连接异常, sessionId: {}", sessionId, e);
                }
            }
        }
    }

    /**
     * 通过 WebSocket 发送二进制音频数据
     */
    private void sendBinaryMessage(WebSocketSession session, byte[] data) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new BinaryMessage(data));
            }
        } catch (IOException e) {
            log.error("WebSocket 发送二进制音频失败", e);
        }
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
