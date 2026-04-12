package com.zunff.interview.service.extend;

import com.alibaba.dashscope.audio.omni.OmniRealtimeCallback;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConfig;
import com.alibaba.dashscope.audio.omni.OmniRealtimeConversation;
import com.alibaba.dashscope.audio.omni.OmniRealtimeModality;
import com.alibaba.dashscope.audio.omni.OmniRealtimeParam;
import com.alibaba.dashscope.audio.omni.OmniRealtimeTranscriptionParam;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import com.zunff.interview.config.AsrConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ASR 实时语音识别服务
 * 使用 DashScope SDK OmniRealtimeConversation + qwen3-asr-flash-realtime
 * 批量模式：接收缓冲的完整音频，通过 WebSocket 流式发送给 DashScope 转录
 */
@Slf4j
@Service
public class AsrRealtimeService {

    private final AsrConfig asrConfig;

    private static final int PCM_CHUNK_SIZE = 3200;     // PCM 格式每块约 0.1s（16kHz, 16bit）
    private static final int SLEEP_INTERVAL_MS = 30;
    private static final long TRANSCRIPTION_TIMEOUT_SECONDS = 120;

    public AsrRealtimeService(AsrConfig asrConfig) {
        this.asrConfig = asrConfig;
        log.info("AsrRealtimeService 初始化: model={}, url={}, language={}, format={}, sampleRate={}",
                asrConfig.getModel(), asrConfig.getUrl(), asrConfig.getLanguage(),
                asrConfig.getInputAudioFormat(), asrConfig.getSampleRate());
    }

    /**
     * 获取配置的输入音频格式
     */
    public String getInputAudioFormat() {
        return asrConfig.getInputAudioFormat();
    }

    /**
     * 转录音频数据（批量模式）
     * 接收缓冲的完整音频字节数组，通过 DashScope SDK 实时 ASR WebSocket 转录
     *
     * @param audioData 原始音频字节数组（PCM 或 Opus 格式）
     * @return 转录文本
     */
    public String transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            log.warn("音频数据为空，跳过 ASR 转录");
            return "";
        }

        log.info("开始 ASR 转录，音频数据大小: {} bytes", audioData.length);

        OmniRealtimeConversation conversation = null;
        try {
            // 1. 构建连接参数
            OmniRealtimeParam param = OmniRealtimeParam.builder()
                    .model(asrConfig.getModel())
                    .url(asrConfig.getUrl())
                    .apikey(asrConfig.getApiKey())
                    .build();

            CountDownLatch finishLatch = new CountDownLatch(1);
            CountDownLatch sessionReadyLatch = new CountDownLatch(1);
            AtomicReference<String> transcribedText = new AtomicReference<>("");

            // 2. 创建回调
            conversation = new OmniRealtimeConversation(param, new OmniRealtimeCallback() {
                @Override
                public void onOpen() {
                    log.debug("ASR Realtime 连接已建立");
                }

                @Override
                public void onEvent(JsonObject message) {
                    String type = message.get("type").getAsString();
                    log.debug("ASR 收到事件: {}, 内容: {}", type, message);
                    switch (type) {
                        case "session.created" -> {
                            String sessionId = message.has("session")
                                    ? message.get("session").getAsJsonObject().get("id").getAsString()
                                    : "unknown";
                            log.debug("ASR Realtime 会话创建: {}", sessionId);
                        }
                        case "session.updated" -> {
                            log.debug("ASR 会话配置已更新，可以开始发送音频");
                            sessionReadyLatch.countDown();
                        }
                        case "conversation.item.input_audio_transcription.completed" -> {
                            String transcript = message.get("transcript").getAsString();
                            transcribedText.set(transcript);
                            log.info("ASR 转录完成: {}", transcript.length() > 100
                                    ? transcript.substring(0, 100) + "..." : transcript);
                            finishLatch.countDown();
                        }
                        case "conversation.item.input_audio_transcription.text" -> {
                            String partialText = message.get("text").getAsString();
                            log.debug("ASR 中间转录结果: {}", partialText);
                        }
                        case "input_audio_buffer.speech_started" -> log.debug("VAD 检测到语音开始");
                        case "input_audio_buffer.speech_stopped" -> log.debug("VAD 检测到语音结束");
                        case "session.finished" -> {
                            log.info("ASR Realtime 会话结束, 完整事件: {}", message);
                            // session.finished 事件可能携带最终转录结果
                            if (message.has("transcript") && !message.get("transcript").isJsonNull()) {
                                String transcript = message.get("transcript").getAsString();
                                if (!transcript.isEmpty()) {
                                    transcribedText.set(transcript);
                                    log.info("ASR session.finished 转录结果: {}", transcript.length() > 100
                                            ? transcript.substring(0, 100) + "..." : transcript);
                                }
                            }
                            finishLatch.countDown();
                        }
                        default -> { /* 忽略其他事件 */ }
                    }
                }

                @Override
                public void onClose(int code, String reason) {
                    log.debug("ASR Realtime 连接关闭: code={}, reason={}", code, reason);
                    finishLatch.countDown();
                }
            });

            // 3. 连接
            conversation.connect();

            // 4. 配置会话
            OmniRealtimeTranscriptionParam transcriptionParam = new OmniRealtimeTranscriptionParam();
            transcriptionParam.setLanguage(asrConfig.getLanguage());
            transcriptionParam.setInputAudioFormat(asrConfig.getInputAudioFormat());
            transcriptionParam.setInputSampleRate(asrConfig.getSampleRate());

            OmniRealtimeConfig config = OmniRealtimeConfig.builder()
                    .modalities(Collections.singletonList(OmniRealtimeModality.TEXT))
                    .transcriptionConfig(transcriptionParam)
                    .build();
            conversation.updateSession(config);

            // 5. 等待 session.updated 事件，确保会话配置已生效
            boolean sessionReady = sessionReadyLatch.await(10, TimeUnit.SECONDS);
            if (!sessionReady) {
                log.warn("等待 ASR 会话配置超时，继续发送音频");
            }

            // 6. 流式发送音频数据
            if ("pcm".equalsIgnoreCase(asrConfig.getInputAudioFormat())) {
                int offset = 0;
                while (offset < audioData.length) {
                    int chunkSize = Math.min(PCM_CHUNK_SIZE, audioData.length - offset);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(audioData, offset, chunk, 0, chunkSize);
                    offset += chunkSize;

                    String audioB64 = Base64.getEncoder().encodeToString(chunk);
                    conversation.appendAudio(audioB64);

                    Thread.sleep(SLEEP_INTERVAL_MS);
                }
            } else {
                // opus 等压缩格式：整块发送，避免帧边界被切断
                log.info("非PCM格式音频，整块发送，大小: {} bytes", audioData.length);
                String audioB64 = Base64.getEncoder().encodeToString(audioData);
                conversation.appendAudio(audioB64);
            }

            // 7. 结束会话
            conversation.endSession();

            // 8. 等待转录完成
            boolean completed = finishLatch.await(TRANSCRIPTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("ASR 转录超时（{}s），返回当前结果", TRANSCRIPTION_TIMEOUT_SECONDS);
            }

            String result = transcribedText.get();
            log.info("ASR 转录结束，文本长度: {}", result != null ? result.length() : 0);
            return result != null ? result : "";

        } catch (InterruptedException e) {
            log.error("ASR 转录被中断", e);
            Thread.currentThread().interrupt();
            return "";
        } catch (NoApiKeyException e) {
            log.error("ASR API Key 未配置", e);
            throw new RuntimeException("ASR API Key 未配置", e);
        } catch (Exception e) {
            log.error("ASR 转录失败", e);
            throw new RuntimeException("ASR 转录失败", e);
        } finally {
            if (conversation != null) {
                try {
                    conversation.close();
                } catch (RuntimeException e) {
                    // SDK 在 session.finished 后可能已自动关闭连接，忽略此异常
                    log.debug("ASR 连接已关闭（预期行为）: {}", e.getMessage());
                } catch (Exception e) {
                    log.warn("关闭 ASR Realtime 连接异常", e);
                }
            }
        }
    }
}
