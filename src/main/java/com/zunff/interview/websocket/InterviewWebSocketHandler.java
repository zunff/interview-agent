package com.zunff.interview.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.model.websocket.ReportMessage;
import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.service.extend.AudioStreamService;
import com.zunff.interview.service.extend.TtsService;
import com.zunff.interview.service.extend.VideoStreamService;
import com.zunff.interview.service.interview.InterviewBusinessService;
import com.zunff.interview.state.InterviewState;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 面试 WebSocket 处理器
 * 处理面试启动、实时视频帧、音频流和面试控制信令
 *
 * 流程：
 * 1. 客户端连接 ws://host/ws/interview
 * 2. 发送 start_interview 消息（含简历、岗位信息）
 * 3. 服务端创建会话，异步执行图，推送 session_created + new_question + TTS
 * 4. 后续通过 video_frame / audio_chunk / answer_complete 交互
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private final VideoStreamService videoStreamService;
    private final AudioStreamService audioStreamService;
    private final InterviewBusinessService interviewBusinessService;
    private final TtsService ttsService;
    private final InterviewSessionService sessionService;

    /** 面试会话ID → WebSocket 会话 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** WebSocket 会话ID → 面试会话ID */
    private final Map<String, String> wsToInterviewSession = new ConcurrentHashMap<>();

    /** TTS 线程映射，用于会话关闭时中断 */
    private final Map<String, Thread> ttsThreads = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket 连接建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JSONObject data = JSONUtil.parseObj(payload);

        String type = data.getStr("type");
        log.trace("收到 WebSocket 消息: type={}, wsSessionId={}", type, session.getId());

        switch (type) {
            case "start_interview" -> handleStartInterview(session, data);
            case "video_frame", "audio_chunk", "answer_complete" -> {
                String interviewSessionId = resolveInterviewSessionId(session, data);
                if (interviewSessionId == null) {
                    sendErrorMessage(session, "未找到面试会话，请先发送 start_interview");
                    return;
                }
                switch (type) {
                    case "video_frame" -> handleVideoFrame(interviewSessionId, data);
                    case "audio_chunk" -> handleAudioChunk(interviewSessionId, data);
                    case "answer_complete" -> handleAnswerComplete(interviewSessionId);
                }
            }
            default -> log.warn("未知的消息类型: {}", type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String interviewSessionId = wsToInterviewSession.remove(session.getId());
        if (interviewSessionId != null) {
            sessions.remove(interviewSessionId);
            interruptTtsThread(interviewSessionId);
            sessionService.disconnectSession(interviewSessionId);
        }
        log.info("WebSocket 连接关闭: {}, 面试会话: {}, 状态: {}", session.getId(), interviewSessionId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String interviewSessionId = wsToInterviewSession.get(session.getId());
        log.error("WebSocket 传输错误: wsSession={}, interviewSession={}", session.getId(), interviewSessionId, exception);
    }

    // ========== 面试启动 ==========

    private void handleStartInterview(WebSocketSession wsSession, JSONObject data) {
        String resume = data.getStr("resume");
        String jobInfo = data.getStr("jobInfo");
        int maxQuestions = data.getInt("maxQuestions", 10);
        int maxFollowUps = data.getInt("maxFollowUps", 2);

        if (resume == null || resume.isEmpty() || jobInfo == null || jobInfo.isEmpty()) {
            sendErrorMessage(wsSession, "简历和岗位信息不能为空");
            return;
        }

        log.info("收到 start_interview 请求，简历长度: {}, 岗位: {}", resume.length(), jobInfo);

        // 创建会话
        InterviewSession session = sessionService.createSession(resume, jobInfo, maxQuestions, maxFollowUps);
        String sessionId = session.getSessionId();

        // 建立映射
        wsToInterviewSession.put(wsSession.getId(), sessionId);
        sessions.put(sessionId, wsSession);

        // 推送 session_created
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.SESSION_CREATED,
                Map.of("sessionId", sessionId)
        ));

        // 异步执行图（耗时较长），完成后推送问题和 TTS
        Thread.ofVirtual().name("interview-start-" + sessionId).start(() -> {
            try {
                InterviewState result = interviewBusinessService.executeInterviewGraph(
                        sessionId, resume, jobInfo, maxQuestions, maxFollowUps);

                if (result == null) {
                    sendErrorMessage(sessionId, "面试启动失败");
                    return;
                }

                String currentQuestion = result.currentQuestion();
                String questionType = result.questionType();
                int questionIndex = result.questionIndex();

                if (currentQuestion != null && !currentQuestion.isEmpty()) {
                    sendQuestion(sessionId, QuestionMessage.builder()
                            .content(currentQuestion)
                            .questionType(questionType)
                            .questionIndex(questionIndex)
                            .build());
                }
            } catch (Exception e) {
                log.error("异步启动面试失败，sessionId: {}", sessionId, e);
                sendErrorMessage(sessionId, "面试启动失败: " + e.getMessage());
            }
        });
    }

    // ========== 消息处理 ==========

    private void handleVideoFrame(String sessionId, JSONObject data) {
        String frameData = data.getStr("frame");
        videoStreamService.handleVideoFrame(sessionId, frameData);
    }

    private void handleAudioChunk(String sessionId, JSONObject data) {
        String audioBase64 = data.getStr("audio");
        if (audioBase64 != null && !audioBase64.isEmpty()) {
            byte[] audioData = Base64.getDecoder().decode(audioBase64);
            audioStreamService.appendChunk(sessionId, audioData);
            log.trace("缓存音频块，会话: {}, 当前缓冲: {} bytes", sessionId, audioStreamService.getBufferSize(sessionId));
        }
    }

    private void handleAnswerComplete(String sessionId) {
        log.info("收到 answer_complete 信号，会话: {}", sessionId);

        List<String> frames = videoStreamService.getFramesForAnalysis(sessionId);
        log.info("从缓存取到 {} 帧视频数据", frames.size());

        String audioBase64 = null;
        byte[] audioData = audioStreamService.getCompleteAudio(sessionId);
        if (audioData != null) {
            audioBase64 = Base64.getEncoder().encodeToString(audioData);
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

    // ========== 推送方法（供 InterviewBusinessService 调用） ==========

    /**
     * 发送问题给前端（文字 + 语音）
     */
    public void sendQuestion(String sessionId, QuestionMessage question) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.NEW_QUESTION,
                question
        ));
        log.info("发送问题到前端: [{}] {}", question.getQuestionType(), question.getContent());

        // 异步触发 TTS 语音合成推送
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            interruptTtsThread(sessionId);
            Thread ttsThread = Thread.ofVirtual().name("tts-" + sessionId).unstarted(() -> {
                try {
                    ttsService.synthesizeAndStream(question.getContent(), sessionId, session);
                } finally {
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

    // ========== 内部工具方法 ==========

    /**
     * 解析面试会话ID：优先从消息体取，否则从映射取
     */
    private String resolveInterviewSessionId(WebSocketSession session, JSONObject data) {
        if (data.containsKey("sessionId")) {
            return data.getStr("sessionId");
        }
        return wsToInterviewSession.get(session.getId());
    }

    private void sendAnswerReceived(String sessionId) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.ANSWER_RECEIVED,
                Map.of("message", "回答已接收，正在评估中...")
        ));
    }

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

    private void sendErrorMessage(String sessionId, String errorMessage) {
        sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.ERROR,
                Map.of("message", errorMessage)
        ));
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            String json = JSONUtil.toJsonStr(WebSocketMessage.of(
                    WebSocketMessage.Type.ERROR,
                    Map.of("message", errorMessage)
            ));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }
}
