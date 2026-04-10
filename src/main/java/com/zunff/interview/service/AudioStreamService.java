package com.zunff.interview.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频流处理服务
 * 管理音频块的缓冲，支持积累后合并为完整音频用于服务端 STT
 */
@Slf4j
@Service
public class AudioStreamService {

    /** 每个会话的音频缓冲区 */
    private final Map<String, ByteArrayOutputStream> sessionBuffers = new ConcurrentHashMap<>();

    /** 最大缓冲大小：10MB */
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;

    /**
     * 积累音频块
     *
     * @param sessionId 会话ID
     * @param audioData 音频数据（原始字节）
     */
    public void appendChunk(String sessionId, byte[] audioData) {
        ByteArrayOutputStream buffer = sessionBuffers.computeIfAbsent(
                sessionId, k -> new ByteArrayOutputStream());
        synchronized (buffer) {
            if (buffer.size() + audioData.length > MAX_BUFFER_SIZE) {
                log.warn("会话 {} 音频缓冲区超过最大限制，丢弃最早的块", sessionId);
                return;
            }
            buffer.write(audioData, 0, audioData.length);
        }
        log.debug("会话 {} 积累音频块，当前大小: {} bytes", sessionId, getBufferSize(sessionId));
    }

    /**
     * 获取完整音频数据并清空缓冲区
     *
     * @param sessionId 会话ID
     * @return 完整音频字节数组，无数据时返回 null
     */
    public byte[] getCompleteAudio(String sessionId) {
        ByteArrayOutputStream buffer = sessionBuffers.get(sessionId);
        if (buffer == null || buffer.size() == 0) {
            return null;
        }
        synchronized (buffer) {
            byte[] completeAudio = buffer.toByteArray();
            buffer.reset();
            log.info("会话 {} 获取完整音频，大小: {} bytes", sessionId, completeAudio.length);
            return completeAudio;
        }
    }

    /**
     * 获取当前缓冲区大小
     */
    public int getBufferSize(String sessionId) {
        ByteArrayOutputStream buffer = sessionBuffers.get(sessionId);
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * 清理会话的音频缓冲区
     */
    public void clearSession(String sessionId) {
        ByteArrayOutputStream removed = sessionBuffers.remove(sessionId);
        if (removed != null) {
            log.info("已清理会话 {} 的音频缓冲区", sessionId);
        }
    }
}
