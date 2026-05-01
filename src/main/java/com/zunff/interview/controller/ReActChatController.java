package com.zunff.interview.controller;

import com.zunff.interview.common.response.ApiResponse;
import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.model.request.SendMessageRequest;
import com.zunff.interview.model.response.AnalysisResponse;
import com.zunff.interview.model.response.CreateSessionResponse;
import com.zunff.interview.model.response.SessionInfoResponse;
import com.zunff.interview.model.response.SessionListItemResponse;
import com.zunff.interview.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.zunff.interview.common.sse.SseHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "简历分析聊天", description = "聊天式简历分析 Agent 接口")
public class ReActChatController {

    private final ChatService chatService;

    @Operation(summary = "创建聊天会话")
    @PostMapping("/sessions")
    public ApiResponse<CreateSessionResponse> createSession() {
        return ApiResponse.success(chatService.createSession());
    }

    @Operation(summary = "发送聊天消息（SSE 流式响应）")
    @PostMapping(value = "/sessions/{sessionId}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable String sessionId,
            @RequestBody SendMessageRequest request) {
        if (request.getMessage() == null || request.getMessage().isEmpty()) {
            SseEmitter emitter = new SseEmitter(300_000L);
            SseHelper.sendError(emitter, "消息不能为空");
            return emitter;
        }
        return chatService.sendMessage(sessionId, request.getMessage());
    }

    @Operation(summary = "上传简历文件（SSE 流式返回分析进度和结果）")
    @PostMapping(value = "/sessions/{sessionId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadResume(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "message", required = false) String message) {
        return chatService.uploadResume(sessionId, file, message);
    }

    @Operation(summary = "获取分析结果")
    @GetMapping("/sessions/{sessionId}/analysis")
    public ApiResponse<AnalysisResponse> getAnalysis(@PathVariable String sessionId) {
        return ApiResponse.success(chatService.getAnalysis(sessionId));
    }

    @Operation(summary = "获取会话信息（含历史消息）")
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionInfoResponse> getSessionInfo(@PathVariable String sessionId) {
        return ApiResponse.success(chatService.getSessionInfo(sessionId));
    }

    @Operation(summary = "获取会话列表（分页）")
    @GetMapping("/sessions")
    public ApiResponse<PageResult<SessionListItemResponse>> listSessions(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(chatService.listSessions(page, size));
    }

    @Operation(summary = "删除（结束）会话")
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return ApiResponse.success();
    }
}