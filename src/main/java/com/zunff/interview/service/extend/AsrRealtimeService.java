package com.zunff.interview.service.extend;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import com.zunff.interview.config.AsrConfig;
import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASR 实时语音识别服务
 * 使用 DashScope SDK Recognition + fun-asr-realtime-2026-02-28
 * 流式模式：前端持续发送 audio_chunk，服务端实时流式发送给 DashScope ASR，转录结果实时缓存
 */
@Slf4j
public class AsrRealtimeService {

    private final String apiKey;

    private final AsrConfig asrConfig;

    /** 每个会话的 Recognition 实例 */
    private final Map<String, Recognition> sessionRecognizers = new ConcurrentHashMap<>();

    /** 每个会话的转录缓存 */
    private final Map<String, List<TranscriptEntry>> sessionTranscripts = new ConcurrentHashMap<>();

    public AsrRealtimeService(String apiKey, AsrConfig asrConfig) {
        this.apiKey = apiKey;
        this.asrConfig = asrConfig;
        // 设置 WebSocket API URL（fun-asr 使用 /inference 端点）
        Constants.baseWebsocketApiUrl = asrConfig.getUrl();
        log.info("AsrRealtimeService 初始化: model={}, url={}, language={}, format={}, sampleRate={}",
                asrConfig.getModel(), asrConfig.getUrl(), asrConfig.getLanguage(),
                asrConfig.getInputAudioFormat(), asrConfig.getSampleRate());
    }

    /**
     * 启动流式ASR识别
     * 建立 WebSocket 长连接，准备接收音频流
     *
     * @param sessionId       面试会话ID
     * @param startTimestampMs 前端传入的录音开始时间戳（ms）
     */
    public void startStreamRecognition(String sessionId, long startTimestampMs) {
        if (sessionRecognizers.containsKey(sessionId)) {
            log.warn("会话 {} 已存在 ASR 连接，先关闭旧连接", sessionId);
            stopStreamRecognition(sessionId);
        }

        log.info("启动流式ASR识别，会话: {}, 开始时间戳: {}", sessionId, startTimestampMs);
        sessionTranscripts.put(sessionId, Collections.synchronizedList(new ArrayList<>()));

        Recognition recognizer = new Recognition();
        sessionRecognizers.put(sessionId, recognizer);

        // 构建回调
        ResultCallback<RecognitionResult> callback = new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult result) {
                try {
                    if (result.isSentenceEnd()) {
                        var sentence = result.getSentence();
                        String text = sentence.getText();
                        Long beginTime = sentence.getBeginTime();
                        Long endTime = sentence.getEndTime();

                        // 计算绝对时间戳
                        long absoluteStart = startTimestampMs + (beginTime != null ? beginTime : 0);
                        long absoluteEnd = startTimestampMs + (endTime != null ? endTime : 0);

                        TranscriptEntry entry = TranscriptEntry.builder()
                                .text(text)
                                .startTimeMs(absoluteStart)
                                .endTimeMs(absoluteEnd)
                                .build();

                        List<TranscriptEntry> transcripts = sessionTranscripts.get(sessionId);
                        if (transcripts != null) {
                            transcripts.add(entry);
                        }

                        log.info("ASR 完整句: \"{}\", 相对时间[{}-{}ms], 绝对时间[{}-{}ms]",
                                text.length() > 50 ? text.substring(0, 50) + "..." : text,
                                beginTime, endTime, absoluteStart, absoluteEnd);
                    } else {
                        var sentence = result.getSentence();
                        log.debug("ASR 中间结果: \"{}\"", sentence.getText());
                    }
                } catch (Exception e) {
                    log.error("处理 ASR 结果异常，会话: {}", sessionId, e);
                }
            }

            @Override
            public void onComplete() {
                log.info("ASR 识别完成，会话: {}", sessionId);
            }

            @Override
            public void onError(Exception e) {
                log.error("ASR 识别错误，会话: {}", sessionId, e);
            }
        };

        // 构建参数
        RecognitionParam.RecognitionParamBuilder<?, ?> paramBuilder = RecognitionParam.builder()
                .model(asrConfig.getModel())
                .apiKey(apiKey)
                .format(asrConfig.getInputAudioFormat())
                .sampleRate(asrConfig.getSampleRate())
                .parameter("heartbeat", true);

        // 配置热词表（如果已设置）
        if (asrConfig.getVocabularyId() != null && !asrConfig.getVocabularyId().isEmpty()) {
            paramBuilder.vocabularyId(asrConfig.getVocabularyId());
            log.info("ASR 热词表已启用，vocabularyId: {}", asrConfig.getVocabularyId());
        }

        RecognitionParam param = paramBuilder.build();

        try {
            recognizer.call(param, callback);
            log.info("ASR 流式识别已启动，会话: {}", sessionId);
        } catch (Exception e) {
            log.error("启动 ASR 流式识别失败，会话: {}", sessionId, e);
            sessionRecognizers.remove(sessionId);
            throw new RuntimeException("启动 ASR 流式识别失败", e);
        }
    }

    /**
     * 发送音频帧
     * 前端每发送一个 audio_chunk，就转发给 ASR 服务
     *
     * @param sessionId 面试会话ID
     * @param audioData 音频数据（原始字节）
     */
    public void sendAudioFrame(String sessionId, byte[] audioData) {
        Recognition recognizer = sessionRecognizers.get(sessionId);
        if (recognizer == null) {
            log.warn("会话 {} 无 ASR 连接，丢弃音频帧 ({}bytes)", sessionId, audioData.length);
            return;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(audioData);
            recognizer.sendAudioFrame(buffer);
            log.trace("发送音频帧，会话: {}, 大小: {}bytes", sessionId, audioData.length);
        } catch (Exception e) {
            log.error("发送音频帧失败，会话: {}", sessionId, e);
        }
    }

    /**
     * 停止流式ASR识别
     * 阻塞直到所有转录结果返回
     *
     * @param sessionId 面试会话ID
     */
    public void stopStreamRecognition(String sessionId) {
        Recognition recognizer = sessionRecognizers.remove(sessionId);
        if (recognizer == null) {
            log.warn("会话 {} 无活跃的 ASR 连接", sessionId);
            return;
        }
        try {
            recognizer.stop();
            log.info("ASR 流式识别已停止，会话: {}", sessionId);
        } catch (Exception e) {
            log.error("停止 ASR 流式识别异常，会话: {}", sessionId, e);
        } finally {
            try {
                recognizer.getDuplexApi().close(1000, "bye");
            } catch (Exception e) {
                log.debug("关闭 ASR WebSocket 连接异常（可能已关闭），会话: {}", sessionId);
            }
        }
    }

    /**
     * 获取转录条目列表
     */
    public List<TranscriptEntry> getTranscriptEntries(String sessionId) {
        List<TranscriptEntry> entries = sessionTranscripts.get(sessionId);
        return entries != null ? new ArrayList<>(entries) : Collections.emptyList();
    }

    /**
     * 获取完整转录文本（所有条目拼接）
     */
    public String getCompleteTranscript(String sessionId) {
        List<TranscriptEntry> entries = sessionTranscripts.get(sessionId);
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(entry.getText());
        }
        return sb.toString();
    }

    /**
     * 获取转录条目数量
     */
    public int getTranscriptCount(String sessionId) {
        List<TranscriptEntry> entries = sessionTranscripts.get(sessionId);
        return entries != null ? entries.size() : 0;
    }

    /**
     * 清理会话的所有ASR资源
     */
    public void clearSession(String sessionId) {
        stopStreamRecognition(sessionId);
        sessionTranscripts.remove(sessionId);
        log.info("已清理会话 {} 的 ASR 资源", sessionId);
    }
}
