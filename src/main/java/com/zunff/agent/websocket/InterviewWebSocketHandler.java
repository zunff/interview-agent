package com.zunff.agent.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.agent.model.bo.EvaluationBO;
import com.zunff.agent.model.dto.websocket.EmotionUpdateMessage;
import com.zunff.agent.model.dto.websocket.QuestionMessage;
import com.zunff.agent.model.dto.websocket.ReportMessage;
import com.zunff.agent.model.dto.websocket.WebSocketMessage;
import com.zunff.agent.model.entity.InterviewSessionEntity;
import com.zunff.agent.service.InterviewSessionService;
import com.zunff.agent.service.VideoStreamService;
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
    private final InterviewSessionService sessionService;

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
        log.debug("接收到音频块，会话: {}", sessionId);
    }

    private void handleAnswerComplete(String sessionId, JSONObject data) {
        String answerText = data.getStr("answerText", "");
        String answerAudio = data.getStr("answerAudio", "");

        log.info("收到完整回答，会话: {}, 文本长度: {}", sessionId, answerText.length());

        List<String> frames = videoStreamService.getFramesForAnalysis(sessionId);
        // 通过业务服务处理回答完成，这里简化处理
        sendAnswerReceived(sessionId);
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

    private String extractSessionId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : session.getId();
    }
}
