package com.zunff.interview.service.extend;

import com.zunff.interview.config.VideoConfig;
import com.zunff.interview.model.dto.analysis.VisionAnalysisResult;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 视频流处理服务
 * 管理视频帧的缓冲和批量分析
 * 每帧携带前端传入的UTC时间戳
 */
@Slf4j
public class VideoStreamService {

    private final long analysisIntervalMs;
    private final int maxFramesPerAnalysis;

    private final MultimodalAnalysisService multimodalAnalysisService;

    /** 每个会话的帧缓冲区 */
    private final Map<String, FrameBuffer> sessionBuffers = new ConcurrentHashMap<>();

    public VideoStreamService(MultimodalAnalysisService multimodalAnalysisService, VideoConfig videoConfig) {
        this.multimodalAnalysisService = multimodalAnalysisService;
        this.analysisIntervalMs = videoConfig.getAnalysisInterval();
        this.maxFramesPerAnalysis = videoConfig.getMaxFramesPerAnalysis();
    }

    /**
     * 接收视频帧
     *
     * @param sessionId    会话ID
     * @param base64Frame  Base64编码的视频帧
     * @param timestampMs  前端传入的UTC时间戳（ms）
     */
    public void handleVideoFrame(String sessionId, String base64Frame, long timestampMs) {
        FrameBuffer buffer = sessionBuffers.computeIfAbsent(
                sessionId,
                k -> new FrameBuffer(analysisIntervalMs, maxFramesPerAnalysis)
        );

        buffer.addFrame(base64Frame, timestampMs);
        log.trace("会话 {} 接收到视频帧，当前缓冲区大小: {}", sessionId, buffer.size());
    }

    /**
     * 获取并清空帧缓冲区用于分析
     *
     * @param sessionId 会话ID
     * @return 帧列表（Base64编码）
     */
    public List<String> getFramesForAnalysis(String sessionId) {
        FrameBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            return Collections.emptyList();
        }
        return buffer.getFramesForAnalysis();
    }

    /**
     * 获取带时间戳的帧列表
     *
     * @param sessionId 会话ID
     * @return 带时间戳的帧列表
     */
    public List<FrameWithTimestamp> getFramesWithTimestamps(String sessionId) {
        FrameBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            return Collections.emptyList();
        }
        return buffer.getFramesWithTimestamps();
    }

    /**
     * 分析视频帧
     *
     * @param sessionId 会话ID
     * @return 分析结果
     */
    public VisionAnalysisResult analyzeFrames(String sessionId) {
        List<String> frames = getFramesForAnalysis(sessionId);
        if (frames.isEmpty()) {
            return VisionAnalysisResult.empty();
        }
        return multimodalAnalysisService.analyzeVideoFrames(frames);
    }

    /**
     * 清理会话的帧缓冲区
     */
    public void clearSession(String sessionId) {
        sessionBuffers.remove(sessionId);
        log.info("已清理会话 {} 的视频缓冲区", sessionId);
    }

    /**
     * 获取会话当前缓冲的帧数量
     */
    public int getBufferSize(String sessionId) {
        FrameBuffer buffer = sessionBuffers.get(sessionId);
        return buffer != null ? buffer.size() : 0;
    }

    /**
     * 帧缓冲区
     * 内部类，用于管理单个会话的视频帧
     */
    private static class FrameBuffer {
        private final LinkedList<FrameWithTimestamp> frames = new LinkedList<>();
        private final long analysisIntervalMs;
        private final int maxFrames;
        private long lastAnalysisTime = 0;

        public FrameBuffer(long analysisIntervalMs, int maxFrames) {
            this.analysisIntervalMs = analysisIntervalMs;
            this.maxFrames = maxFrames;
        }

        public synchronized void addFrame(String base64Frame, long timestampMs) {
            frames.offer(new FrameWithTimestamp(base64Frame, timestampMs));

            // 保持最大帧数限制
            while (frames.size() > maxFrames) {
                frames.poll();
            }
        }

        public synchronized int size() {
            return frames.size();
        }

        public synchronized boolean shouldAnalyze() {
            return System.currentTimeMillis() - lastAnalysisTime >= analysisIntervalMs;
        }

        public synchronized List<String> getFramesForAnalysis() {
            lastAnalysisTime = System.currentTimeMillis();
            List<String> result = new ArrayList<>();
            for (FrameWithTimestamp frame : frames) {
                result.add(frame.frame);
            }
            return result;
        }

        public synchronized List<FrameWithTimestamp> getFramesWithTimestamps() {
            lastAnalysisTime = System.currentTimeMillis();
            return new ArrayList<>(frames);
        }
    }

    public record FrameWithTimestamp(String frame, long timestampMs) {}
}
