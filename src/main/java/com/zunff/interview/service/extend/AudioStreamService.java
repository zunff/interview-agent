package com.zunff.interview.service.extend;

import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 音频流处理服务
 * 管理实时ASR转录结果的缓存
 * 前端 audio_chunk → 直接转发给 AsrRealtimeService 实时转录 → 缓存 TranscriptEntry
 */
@Slf4j
@Service
public class AudioStreamService {

    private final AsrRealtimeService asrRealtimeService;

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
        asrRealtimeService.startStreamRecognition(sessionId, startTimestampMs);
    }

    /**
     * 积累音频块（转发给ASR实时转录）
     *
     * @param sessionId 会话ID
     * @param audioData 音频数据（原始字节）
     */
    public void appendChunk(String sessionId, byte[] audioData) {
        asrRealtimeService.sendAudioFrame(sessionId, audioData);
        log.trace("会话 {} 转发音频块到ASR，大小: {} bytes, 当前转录条目数: {}",
                sessionId, audioData.length, asrRealtimeService.getTranscriptCount(sessionId));
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
     * 获取完整转录文本（所有条目拼接）并清理缓存
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
     * 获取当前转录条目数量
     */
    public int getBufferSize(String sessionId) {
        return asrRealtimeService.getTranscriptCount(sessionId);
    }

    /**
     * 清理会话的音频缓冲区
     */
    public void clearSession(String sessionId) {
        asrRealtimeService.clearSession(sessionId);
        log.info("已清理会话 {} 的音频缓冲区", sessionId);
    }
}
