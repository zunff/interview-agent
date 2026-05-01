package com.zunff.interview.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseEventType;
import com.zunff.interview.common.sse.SseHelper;
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
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private static final int MAX_TOOL_ROUNDS = 5;

    private final ChatModel chatModel;
    private final ChatSessionMapper chatSessionMapper;
    private final ResumeAnalysisMapper resumeAnalysisMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final PromptTemplateService promptTemplateService;
    private final CompiledGraph<ResumeState> resumeAnalysisGraph;
    private final SseEmitterRegistry emitterRegistry;
    private final ExecutorService virtualThreadExecutor;
    private final ChatMemory chatMemory;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallingChatOptions chatOptions;

    public ChatServiceImpl(ChatModel chatModel,
                           ToolCallbackProvider resumeAnalysisTools,
                           ChatSessionMapper chatSessionMapper,
                           ResumeAnalysisMapper resumeAnalysisMapper,
                           ChatMemoryMapper chatMemoryMapper,
                           PromptTemplateService promptTemplateService,
                           CompiledGraph<ResumeState> resumeAnalysisGraph,
                           SseEmitterRegistry emitterRegistry,
                           ChatMemory chatMemory,
                           @org.springframework.beans.factory.annotation.Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.chatModel = chatModel;
        this.chatSessionMapper = chatSessionMapper;
        this.resumeAnalysisMapper = resumeAnalysisMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.promptTemplateService = promptTemplateService;
        this.resumeAnalysisGraph = resumeAnalysisGraph;
        this.emitterRegistry = emitterRegistry;
        this.chatMemory = chatMemory;
        this.virtualThreadExecutor = virtualThreadExecutor;

        // 初始化一次，全局复用
        ToolCallback[] toolCallbacks = resumeAnalysisTools.getToolCallbacks();
        this.toolCallingManager = ToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(Arrays.asList(toolCallbacks)))
                .build();
        this.chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
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
    public SseEmitter sendMessage(String sessionId, String message) {
        validateSession(sessionId);

        SseEmitter emitter = new SseEmitter(300_000L);
        emitterRegistry.register(sessionId, emitter);

        virtualThreadExecutor.submit(() -> {
            try {
                String systemPrompt = promptTemplateService.getPrompt("resume-chat-system");
                StringBuilder promptBuilder = new StringBuilder(systemPrompt);
                appendAnalysisContext(sessionId, promptBuilder);

                List<Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(promptBuilder.toString()));
                messages.addAll(chatMemory.get(sessionId));
                messages.add(new UserMessage(message));
                chatMemory.add(sessionId, new UserMessage(message));

                Prompt prompt = new Prompt(messages, chatOptions);

                // === 阶段 1：思考（ReAct 循环）===
                SseHelper.sendThinkingStart(emitter);
                String summary = null;

                for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                    log.info("ReAct round {}: sessionId={}", round + 1, sessionId);

                    ChatResponse response = chatModel.call(prompt);
                    AssistantMessage assistant = response.getResult().getOutput();
                    List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();

                    if (toolCalls.isEmpty()) {
                        // 无工具调用
                        summary = assistant.getText();
                        chatMemory.add(sessionId, assistant);
                        break;
                    }

                    // 检查是否调用了 finishChat
                    boolean finished = false;
                    for (AssistantMessage.ToolCall tc : toolCalls) {
                        if ("finishChat".equals(tc.name())) {
                            // 从 arguments JSON 中提取 summary
                            JSONObject args = JSONUtil.parseObj(tc.arguments());
                            summary = args.getStr("summary");
                            finished = true;
                            break;
                        }
                        // 非结束工具 → 推送 tool_status
                        emitter.send(SseEmitter.event()
                                .name(SseEventType.TOOL_STATUS.name().toLowerCase())
                                .data(JSONUtil.toJsonStr(Map.of(
                                        "tool", tc.name(),
                                        "state", "running"
                                ))));
                    }

                    // 执行工具
                    chatMemory.add(sessionId, assistant);
                    ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
                    List<Message> history = result.conversationHistory();
                    if (!history.isEmpty()) {
                        chatMemory.add(sessionId, history.getLast());
                    }

                    if (finished) break;
                    prompt = new Prompt(history, chatOptions);
                }

                SseHelper.sendThinkingEnd(emitter, summary != null ? summary : "");

                // === 阶段 2：流式输出 ===
                if (summary != null && !summary.isEmpty()) {
                    streamFinalResponse(emitter, sessionId, summary);
                }
                SseHelper.sendDone(emitter);

            } catch (Exception e) {
                log.error("处理失败, sessionId: {}", sessionId, e);
                SseHelper.sendError(emitter, e.getMessage() != null ? e.getMessage() : "处理失败");
            }
        });

        return emitter;
    }

    private void streamFinalResponse(SseEmitter emitter, String sessionId, String summary) {
        try {
            // 构造提示：基于 summary 生成详细回答
            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(
                    "请基于以下思考总结，给用户一个详细、格式化的回答：\n\n" + summary
            ));
            Prompt streamPrompt = new Prompt(messages);  // 不带工具

            StringBuilder fullResponse = new StringBuilder();
            chatModel.stream(streamPrompt)
                    .doOnNext(chunk -> {
                        String text = chunk.getResult().getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            fullResponse.append(text);
                            try {
                                emitter.send(SseEmitter.event().name("message").data(text));
                            } catch (IOException e) {
                                log.warn("SSE 发送文本失败: {}", e.getMessage());
                            }
                        }
                    })
                    .blockLast();

            // 记录最终回答
            if (!fullResponse.isEmpty()) {
                chatMemory.add(sessionId, new AssistantMessage(fullResponse.toString()));
            }
        } catch (Exception e) {
            log.error("流式输出失败, sessionId: {}", sessionId, e);
        }
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
