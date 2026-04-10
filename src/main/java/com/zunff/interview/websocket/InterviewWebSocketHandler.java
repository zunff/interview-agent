package com.zunff.interview.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.request.SubmitAnswerRequest;
import com.zunff.interview.model.dto.websocket.EmotionUpdateMessage;
import com.zunff.interview.model.dto.websocket.QuestionMessage;
import com.zunff.interview.model.dto.websocket.ReportMessage;
import com.zunff.interview.model.dto.websocket.WebSocketMessage;
import com.zunff.interview.model.entity.InterviewSessionEntity;
import com.zunff.interview.service.AudioStreamService;
import com.zunff.interview.service.InterviewBusinessService;
import com.zunff.interview.service.InterviewSessionService;
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
    private final InterviewSessionService sessionService;
    private final InterviewBusinessService interviewBusinessService;

    /** WebSocket 会话映射 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

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
            case "answer_complete" -> handleAnswerComplete(interviewSessionId, data);
            case "emotion_update" -> handleEmotionUpdate(interviewSessionId, data);
            default -> log.warn("未知的消息类型: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        sessions.remove(sessionId);
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

        if (videoStreamService.getBufferSize(sessionId) >= 5) {
            var result = videoStreamService.analyzeFrames(sessionId);
            sendEmotionUpdate(sessionId, result.getEmotionScore(), result.getBodyLanguageScore());
        }
    }

    private void handleAudioChunk(String sessionId, JSONObject data) {
        var session = sessionService.getBySessionId(sessionId);
        if (session != null) {
            sessionService.updateStatus(sessionId, InterviewSessionEntity.Status.WAITING_ANSWER.name());
        }

        String audioBase64 = data.getStr("audio");
        if (audioBase64 != null && !audioBase64.isEmpty()) {
            byte[] audioData = java.util.Base64.getDecoder().decode(audioBase64);
            audioStreamService.appendChunk(sessionId, audioData);
            log.debug("积累音频块，会话: {}, 当前缓冲: {} bytes", sessionId, audioStreamService.getBufferSize(sessionId));
        } else {
            log.debug("接收到音频块但无数据，会话: {}", sessionId);
        }
    }

    private void handleAnswerComplete(String sessionId, JSONObject data) {
        String answerText = data.getStr("answerText", "");
        String answerAudio = data.getStr("answerAudio", "");

        log.info("收到完整回答，会话: {}, 文本长度: {}", sessionId, answerText.length());

        // 如果没有直接传入音频，尝试从缓冲区获取积累的音频块
        if ((answerAudio == null || answerAudio.isEmpty()) && audioStreamService.getBufferSize(sessionId) > 0) {
            byte[] completeAudio = audioStreamService.getCompleteAudio(sessionId);
            if (completeAudio != null) {
                answerAudio = java.util.Base64.getEncoder().encodeToString(completeAudio);
                log.info("使用缓冲区音频，大小: {} bytes", completeAudio.length);
            }
        }

        sendAnswerReceived(sessionId);

        // 通过业务服务处理回答
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .sessionId(sessionId)
                .answerText(answerText.isEmpty() ? null : answerText)
                .answerAudio(answerAudio)
                .build();

        try {
            interviewBusinessService.submitAnswer(request);
        } catch (Exception e) {
            log.error("WebSocket 提交答案失败，会话: {}", sessionId, e);
            sendErrorMessage(sessionId, "答案处理失败: " + e.getMessage());
        }
    }

    private void handleEmotionUpdate(String sessionId, JSONObject data) {
        log.debug("收到情感更新: {}", sessionId);
    }

    /**
     * 发送问题给前端
     */
    public void sendQuestion(String sessionId, QuestionMessage question) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.NEW_QUESTION,
                question
        ));
        log.info("发送问题到前端: [{}] {}", question.getQuestionType(), question.getContent());
    }

    /**
     * 发送情感更新
     */
    public void sendEmotionUpdate(String sessionId, int emotionScore, int bodyLanguageScore) {
        EmotionUpdateMessage message = EmotionUpdateMessage.builder()
                .emotionScore(emotionScore)
                .bodyLanguageScore(bodyLanguageScore)
                .build();
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.EMOTION_UPDATE,
                message
        ));
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
