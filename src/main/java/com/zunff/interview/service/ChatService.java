package com.zunff.interview.service;

import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.model.response.AnalysisResponse;
import com.zunff.interview.model.response.CreateSessionResponse;
import com.zunff.interview.model.response.SessionInfoResponse;
import com.zunff.interview.model.response.SessionListItemResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

public interface ChatService {

    CreateSessionResponse createSession();

    Flux<ServerSentEvent<String>> sendMessage(String sessionId, String message);

    SseEmitter uploadResume(String sessionId, MultipartFile file, String message);

    AnalysisResponse getAnalysis(String sessionId);

    SessionInfoResponse getSessionInfo(String sessionId);

    PageResult<SessionListItemResponse> listSessions(int page, int size);

    void deleteSession(String sessionId);
}
