package com.zunff.interview.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.request.SubmitAnswerRequest;
import com.zunff.interview.model.dto.websocket.QuestionMessage;
import com.zunff.interview.model.dto.websocket.ReportMessage;
import com.zunff.interview.model.dto.websocket.WebSocketMessage;
import com.zunff.interview.service.AudioStreamService;
import com.zunff.interview.service.InterviewBusinessService;
import com.zunff.interview.service.TtsService;
import com.zunff.interview.service.VideoStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试 WebSocket 处理器
 * 处理实时视频帧、音频流和面试控制信令
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private final VideoStreamService videoStreamService;
    private final AudioStreamService audioStreamService;
    private final InterviewBusinessService interviewBusinessService;
    private final TtsService ttsService;

    /** WebSocket 会话映射 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** TTS 线程映射，用于会话关闭时中断 */
    private final Map<String, Thread> ttsThreads = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        sessions.put(sessionId, session);
        log.info("WebSocket 连接建立: {}, 会话ID: {}", session.getId(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JSONObject data = JSONUtil.parseObj(payload);

        String type = data.getStr("type");
        String interviewSessionId = data.containsKey("sessionId")
                ? data.getStr("sessionId")
                : extractSessionId(session);

        log.debug("收到 WebSocket 消息: type={}, sessionId={}", type, interviewSessionId);

        switch (type) {
            case "video_frame" -> handleVideoFrame(interviewSessionId, data);
            case "audio_chunk" -> handleAudioChunk(interviewSessionId, data);
            case "answer_complete" -> handleAnswerComplete(interviewSessionId);
            default -> log.warn("未知的消息类型: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        sessions.remove(sessionId);
        // 中断该会话的 TTS 线程，节省资源
        interruptTtsThread(sessionId);
        log.info("WebSocket 连接关闭: {}, 状态: {}", sessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = extractSessionId(session);
        log.error("WebSocket 传输错误: {}", sessionId, exception);
    }

    private void handleVideoFrame(String sessionId, JSONObject data) {
        String frameData = data.getStr("frame");
        videoStreamService.handleVideoFrame(sessionId, frameData);
    }

    private void handleAudioChunk(String sessionId, JSONObject data) {
        String audioBase64 = data.getStr("audio");
        if (audioBase64 != null && !audioBase64.isEmpty()) {
            byte[] audioData = java.util.Base64.getDecoder().decode(audioBase64);
            audioStreamService.appendChunk(sessionId, audioData);
            log.debug("缓存音频块，会话: {}, 当前缓冲: {} bytes", sessionId, audioStreamService.getBufferSize(sessionId));
        }
    }

    private void handleAnswerComplete(String sessionId) {
        log.info("收到 answer_complete 信号，会话: {}", sessionId);

        // 从缓存取关键帧
        List<String> frames = videoStreamService.getFramesForAnalysis(sessionId);
        log.info("从缓存取到 {} 帧视频数据", frames.size());

        // 从缓存取音频
        String audioBase64 = null;
        byte[] audioData = audioStreamService.getCompleteAudio(sessionId);
        if (audioData != null) {
            audioBase64 = java.util.Base64.getEncoder().encodeToString(audioData);
            log.info("从缓存取到音频数据，大小: {} bytes", audioData.length);
        }

        sendAnswerReceived(sessionId);

        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .sessionId(sessionId)
                .videoFrames(frames.isEmpty() ? null : String.join(",", frames))
                .answerAudio(audioBase64)
                .build();

        try {
            interviewBusinessService.submitAnswer(request);
        } catch (Exception e) {
            log.error("WebSocket 提交答案失败，会话: {}", sessionId, e);
            sendErrorMessage(sessionId, "答案处理失败: " + e.getMessage());
        }
    }

    /**
     * 发送问题给前端（文字 + 语音）
     */
    public void sendQuestion(String sessionId, QuestionMessage question) {
        // 1. 先发文字问题
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.NEW_QUESTION,
                question
        ));
        log.info("发送问题到前端: [{}] {}", question.getQuestionType(), question.getContent());

        // 2. 异步触发 TTS 语音合成推送
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            // 先中断之前的 TTS 线程（如有）
            interruptTtsThread(sessionId);
            // 创建新线程并保存句柄
            Thread ttsThread = Thread.ofVirtual().name("tts-" + sessionId).unstarted(() -> {
                try {
                    ttsService.synthesizeAndStream(question.getContent(), sessionId, session);
                } finally {
                    // 线程完成后清理句柄
                    ttsThreads.remove(sessionId);
                }
            });
            ttsThreads.put(sessionId, ttsThread);
            ttsThread.start();
        }
    }

    /**
     * 中断指定会话的 TTS 线程
     */
    private void interruptTtsThread(String sessionId) {
        Thread thread = ttsThreads.remove(sessionId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("已中断 TTS 线程: {}", sessionId);
        }
    }

    /**
     * 发送评估结果
     */
    public void sendEvaluationResult(String sessionId, EvaluationBO evaluation) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.EVALUATION_RESULT,
                evaluation
        ));
    }

    /**
     * 发送最终报告
     */
    public void sendFinalReport(String sessionId, String report) {
        ReportMessage message = ReportMessage.builder()
                .report(report)
                .build();
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.FINAL_REPORT,
                message
        ));
    }

    /**
     * 发送回答已接收确认
     */
    private void sendAnswerReceived(String sessionId) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.ANSWER_RECEIVED,
                Map.of("message", "回答已接收，正在评估中...")
        ));
    }

    /**
     * 发送消息到前端
     */
    private <T> void sendMessage(String sessionId, WebSocketMessage<T> message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                String json = JSONUtil.toJsonStr(message);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.error("发送消息失败: {}", sessionId, e);
            }
        } else if (messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/interview/" + sessionId, message);
        } else {
            log.warn("无法发送消息，没有可用的 WebSocket 会话: {}", sessionId);
        }
    }

    /**
     * 发送错误消息
     */
    private void sendErrorMessage(String sessionId, String errorMessage) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.ERROR,
                Map.of("message", errorMessage)
        ));
    }

    private String extractSessionId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : session.getId();
    }
}
