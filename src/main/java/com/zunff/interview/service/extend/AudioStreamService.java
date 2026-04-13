package com.zunff.interview.service.extend;

import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频流处理服务
 * 管理实时ASR转录结果的缓存 + 原始PCM字节缓存（用于Omni多模态分析）
 * 前端 audio_chunk → 转发给 AsrRealtimeService 实时转录 → 缓存 TranscriptEntry
 * 同时缓存原始 PCM 字节供 Omni 音频副语言分析使用
 */
@Slf4j
@Service
public class AudioStreamService {

    private final AsrRealtimeService asrRealtimeService;

    /** 每个会话的原始PCM缓冲区（用于Omni音频分析） */
    private final Map<String, ByteArrayOutputStream> sessionPcmBuffers = new ConcurrentHashMap<>();

    /** 最大PCM缓冲大小：10MB */
    private static final int MAX_PCM_BUFFER_SIZE = 10 * 1024 * 1024;

    public AudioStreamService(AsrRealtimeService asrRealtimeService) {
        this.asrRealtimeService = asrRealtimeService;
    }

    /**
     * 启动实时ASR识别
     *
     * @param sessionId       会话ID
     * @param startTimestampMs 前端传入的录音开始时间戳（ms）
     */
    public void startRealtimeAsr(String sessionId, long startTimestampMs) {
        log.info("启动实时ASR，会话: {}, 开始时间戳: {}", sessionId, startTimestampMs);
        sessionPcmBuffers.put(sessionId, new ByteArrayOutputStream());
        asrRealtimeService.startStreamRecognition(sessionId, startTimestampMs);
    }

    /**
     * 积累音频块：转发给ASR实时转录 + 缓存原始PCM字节
     *
     * @param sessionId 会话ID
     * @param audioData 音频数据（原始字节）
     */
    public void appendChunk(String sessionId, byte[] audioData) {
        // 1. 转发给ASR实时转录
        asrRealtimeService.sendAudioFrame(sessionId, audioData);

        // 2. 缓存原始PCM字节（用于Omni音频分析）
        ByteArrayOutputStream pcmBuffer = sessionPcmBuffers.get(sessionId);
        if (pcmBuffer != null) {
            synchronized (pcmBuffer) {
                if (pcmBuffer.size() + audioData.length <= MAX_PCM_BUFFER_SIZE) {
                    pcmBuffer.write(audioData, 0, audioData.length);
                } else {
                    log.warn("会话 {} PCM缓冲区超过最大限制({}MB)，丢弃当前块",
                            sessionId, MAX_PCM_BUFFER_SIZE / 1024 / 1024);
                }
            }
        }

        log.trace("会话 {} 转发音频块到ASR，大小: {} bytes, 当前转录条目数: {}, PCM缓冲: {} bytes",
                sessionId, audioData.length,
                asrRealtimeService.getTranscriptCount(sessionId),
                getPcmBufferSize(sessionId));
    }

    /**
     * 停止实时ASR识别
     *
     * @param sessionId 会话ID
     */
    public void stopRealtimeAsr(String sessionId) {
        log.info("停止实时ASR，会话: {}", sessionId);
        asrRealtimeService.stopStreamRecognition(sessionId);
    }

    /**
     * 获取完整转录文本（所有条目拼接）
     *
     * @param sessionId 会话ID
     * @return 拼接的转录文本，无数据时返回空字符串
     */
    public String getCompleteTranscript(String sessionId) {
        String transcript = asrRealtimeService.getCompleteTranscript(sessionId);
        log.info("会话 {} 获取完整转录文本，长度: {}", sessionId, transcript.length());
        return transcript;
    }

    /**
     * 获取转录条目列表（带时间戳）
     *
     * @param sessionId 会话ID
     * @return 转录条目列表
     */
    public List<TranscriptEntry> getTranscriptEntries(String sessionId) {
        return asrRealtimeService.getTranscriptEntries(sessionId);
    }

    /**
     * 获取缓存的完整PCM音频数据并清空缓冲区（用于Omni音频分析）
     *
     * @param sessionId 会话ID
     * @return PCM字节数组，无数据时返回 null
     */
    public byte[] getCompletePcmAudio(String sessionId) {
        ByteArrayOutputStream buffer = sessionPcmBuffers.get(sessionId);
        if (buffer == null || buffer.size() == 0) {
            return null;
        }
        synchronized (buffer) {
            byte[] pcmData = buffer.toByteArray();
            buffer.reset();
            log.info("会话 {} 获取完整PCM音频，大小: {} bytes", sessionId, pcmData.length);
            return pcmData;
        }
    }

    /**
     * 获取当前PCM缓冲区大小
     */
    public int getPcmBufferSize(String sessionId) {
        ByteArrayOutputStream buffer = sessionPcmBuffers.get(sessionId);
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * 获取当前转录条目数量
     */
    public int getBufferSize(String sessionId) {
        return asrRealtimeService.getTranscriptCount(sessionId);
    }

    /**
     * 清理会话的所有音频资源
     */
    public void clearSession(String sessionId) {
        asrRealtimeService.clearSession(sessionId);
        sessionPcmBuffers.remove(sessionId);
        log.info("已清理会话 {} 的音频缓冲区", sessionId);
    }
}
