package com.zunff.interview.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.exception.BusinessException;
import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.mapper.ChatMemoryMapper;
import com.zunff.interview.mapper.ChatSessionMapper;
import com.zunff.interview.mapper.ResumeAnalysisMapper;
import com.zunff.interview.model.entity.ChatMemoryRecord;
import com.zunff.interview.model.entity.ChatSession;
import com.zunff.interview.model.entity.ResumeAnalysis;
import com.zunff.interview.model.response.*;
import com.zunff.interview.service.ChatService;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/resume-uploads";

    private final ChatClient resumeChatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatSessionMapper chatSessionMapper;
    private final ResumeAnalysisMapper resumeAnalysisMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final PromptTemplateService promptTemplateService;
    private final CompiledGraph<ResumeState> resumeAnalysisGraph;
    private final SseEmitterRegistry emitterRegistry;
    private final ExecutorService virtualThreadExecutor;

    public ChatServiceImpl(ChatClient resumeChatClient,
                           ToolCallbackProvider resumeAnalysisTools,
                           ChatSessionMapper chatSessionMapper,
                           ResumeAnalysisMapper resumeAnalysisMapper,
                           ChatMemoryMapper chatMemoryMapper,
                           PromptTemplateService promptTemplateService,
                           CompiledGraph<ResumeState> resumeAnalysisGraph,
                           SseEmitterRegistry emitterRegistry,
                           @org.springframework.beans.factory.annotation.Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.resumeChatClient = resumeChatClient;
        this.toolCallbackProvider = resumeAnalysisTools;
        this.chatSessionMapper = chatSessionMapper;
        this.resumeAnalysisMapper = resumeAnalysisMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.promptTemplateService = promptTemplateService;
        this.resumeAnalysisGraph = resumeAnalysisGraph;
        this.emitterRegistry = emitterRegistry;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public CreateSessionResponse createSession() {
        String sessionId = IdUtil.simpleUUID();

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.ACTIVE.name())
                .createTime(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);

        log.info("创建聊天会话: {}", sessionId);
        return CreateSessionResponse.builder()
                .sessionId(sessionId)
                .status("ACTIVE")
                .build();
    }

    @Override
    public SseEmitter uploadResume(String sessionId, MultipartFile file, String message) {
        ChatSession session = getSession(sessionId);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(2001, "文件名不能为空");
        }
        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        if (!Set.of("pdf", "docx", "doc", "txt").contains(fileExtension)) {
            throw new BusinessException(2002, "不支持的文件类型: " + fileExtension);
        }

        try {
            Files.createDirectories(Path.of(UPLOAD_DIR));
        } catch (IOException e) {
            throw new BusinessException(2003, "创建上传目录失败");
        }

        String savedFileName = sessionId + "_resume." + fileExtension;
        Path filePath = Path.of(UPLOAD_DIR, savedFileName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException(2004, "文件保存失败");
        }

        session.setResumeFilePath(filePath.toString());
        chatSessionMapper.updateById(session);

        SseEmitter emitter = new SseEmitter(300_000L);
        emitterRegistry.register(sessionId, emitter);

        virtualThreadExecutor.submit(() -> {
            try {
                log.info("Phase 1 图开始执行, sessionId: {}", sessionId);
                Map<String, Object> initialState = new HashMap<>();
                initialState.put(ResumeState.SESSION_ID, sessionId);
                initialState.put(ResumeState.FILE_PATH, filePath.toString());
                initialState.put(ResumeState.FILE_NAME, originalFilename);
                initialState.put(ResumeState.FILE_TYPE, fileExtension);

                RunnableConfig config = RunnableConfig.builder()
                        .threadId(sessionId)
                        .addParallelNodeExecutor("company", virtualThreadExecutor)
                        .build();

                var result = resumeAnalysisGraph.invoke(initialState, config);
                log.info("Phase 1 图执行完成, sessionId: {}, result present: {}", sessionId, result.isPresent());

                // 报告上下文由 sendMessage 的 appendAnalysisContext 从数据库读取，
                // 不再注入 ChatMemory，避免与 system prompt 中的报告内容重复
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
                log.error("Phase 1 图执行失败, sessionId: {}, error: {}", sessionId, errorMsg, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + errorMsg.replace("\"", "'") + "\"}"));
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        });

        return emitter;
    }

    @Override
    public Flux<ServerSentEvent<String>> sendMessage(String sessionId, String message) {
        validateSession(sessionId);

        String systemPrompt = promptTemplateService.getPrompt("resume-chat-system");

        StringBuilder promptBuilder = new StringBuilder(systemPrompt);
        appendAnalysisContext(sessionId, promptBuilder);

        Flux<ChatResponse> chatFlux = resumeChatClient.prompt()
                .system(promptBuilder.toString())
                .user(message)
                .toolCallbacks(toolCallbackProvider.getToolCallbacks())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse();

        return chatFlux.flatMap(response -> {
            if (response.hasToolCalls()) {
                // 检查是否有 ToolCalls 详情
                response.getResult();
                response.getResult();
                response.getResult();
                List<AssistantMessage.ToolCall> toolCalls =
                    response.getResult().getOutput().getToolCalls();

                // 提取工具信息
                List<Map<String, Object>> toolInfo = toolCalls.stream()
                    .map(tc -> Map.<String, Object>of(
                        "name", tc.name(),
                        "arguments", tc.arguments()
                    ))
                    .toList();

                // 发送详细的工具状态
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("tool_status")
                    .data(cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                        "state", "running",
                        "tools", toolInfo
                    )))
                    .build());
            }

            response.getResult();
            response.getResult();
            String content = response.getResult().getOutput().getText();

            if (content != null && !content.isEmpty()) {
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("message")
                        .data(content)
                        .build());
            }
            return Flux.empty();
        }).concatWith(Flux.just(
                ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()
        ));
    }

    private void appendAnalysisContext(String sessionId, StringBuilder promptBuilder) {
        try {
            ResumeAnalysis analysis = resumeAnalysisMapper.selectOne(
                    new LambdaQueryWrapper<ResumeAnalysis>()
                            .eq(ResumeAnalysis::getSessionId, sessionId)
                            .orderByDesc(ResumeAnalysis::getCreateTime)
                            .last("LIMIT 1"));
            if (analysis != null && analysis.getReportMarkdown() != null) {
                promptBuilder.append("\n\n## 当前会话的分析报告\n\n");
                promptBuilder.append(analysis.getReportMarkdown());
            }
        } catch (Exception e) {
            log.warn("获取分析结果失败, sessionId: {}", sessionId, e);
        }
    }

    @Override
    public AnalysisResponse getAnalysis(String sessionId) {
        validateSession(sessionId);

        ResumeAnalysis analysis = resumeAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ResumeAnalysis>()
                        .eq(ResumeAnalysis::getSessionId, sessionId)
                        .orderByDesc(ResumeAnalysis::getCreateTime)
                        .last("LIMIT 1"));

        if (analysis == null) {
            return AnalysisResponse.builder().hasAnalysis(false).build();
        }

        return AnalysisResponse.builder()
                .hasAnalysis(true)
                .analysisId(analysis.getAnalysisId())
                .radarChartData(analysis.getRadarChartData() != null ? analysis.getRadarChartData() : "{}")
                .reportMarkdown(analysis.getReportMarkdown())
                .build();
    }

    @Override
    public SessionInfoResponse getSessionInfo(String sessionId) {
        ChatSession session = getSession(sessionId);

        List<ChatMemoryRecord> records = chatMemoryMapper.selectList(
                new LambdaQueryWrapper<ChatMemoryRecord>()
                        .eq(ChatMemoryRecord::getConversationId, sessionId)
                        .orderByAsc(ChatMemoryRecord::getTimestamp));

        List<SessionInfoResponse.ChatMessageItem> messages = records.stream()
                .map(r -> SessionInfoResponse.ChatMessageItem.builder()
                        .role(r.getType() != null ? r.getType().toLowerCase() : "user")
                        .content(r.getContent())
                        .build())
                .toList();

        return SessionInfoResponse.builder()
                .sessionId(session.getSessionId())
                .hasResume(session.getResumeFilePath() != null)
                .status(session.getStatus())
                .messages(messages)
                .build();
    }

    @Override
    public PageResult<SessionListItemResponse> listSessions(int page, int size) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .orderByDesc(ChatSession::getCreateTime);

        Page<ChatSession> pageResult = chatSessionMapper.selectPage(new Page<>(page, size), wrapper);

        List<SessionListItemResponse> records = pageResult.getRecords().stream()
                .map(s -> SessionListItemResponse.builder()
                        .sessionId(s.getSessionId())
                        .hasResume(s.getResumeFilePath() != null)
                        .status(s.getStatus())
                        .createTime(s.getCreateTime() != null ? s.getCreateTime().toString() : "")
                        .build())
                .toList();

        return PageResult.of(pageResult, records);
    }

    @Override
    public void deleteSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        session.setStatus(ChatSession.Status.ENDED.name());
        session.setEndTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    private void validateSession(String sessionId) {
        boolean exists = chatSessionMapper.exists(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (!exists) {
            throw new BusinessException(2001, "会话不存在: " + sessionId);
        }
    }

    private ChatSession getSession(String sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (session == null) {
            throw new BusinessException(2001, "会话不存在: " + sessionId);
        }
        return session;
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
}
