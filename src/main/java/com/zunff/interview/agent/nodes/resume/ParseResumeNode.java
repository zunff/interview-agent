package com.zunff.interview.agent.nodes.resume;

import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import com.zunff.interview.mapper.ChatSessionMapper;
import com.zunff.interview.model.entity.ChatSession;
import com.zunff.interview.utils.FileParserUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParseResumeNode {

    private final FileParserUtils fileParserUtils;
    private final SseEmitterRegistry emitterRegistry;
    private final ChatSessionMapper chatSessionMapper;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        log.info("[ParseResume] 解析简历, sessionId: {}", state.sessionId());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "parse", "running");

        Map<String, Object> updates = new HashMap<>();
        FileParserUtils.Response result = fileParserUtils.parse(state.filePath());

        if (result.success()) {
            updates.put(ResumeState.RESUME_TEXT, result.text());
            updateResumeText(state.sessionId(), result.text());
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "parse", "completed");
            log.info("[ParseResume] 解析完成, 文本长度: {}", result.text().length());
            deleteSourceFile(state.filePath());
        } else {
            updates.put(ResumeState.ERROR, "简历解析失败: " + result.error());
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "parse", "failed");
            log.error("[ParseResume] 解析失败: {}", result.error());
            deleteSourceFile(state.filePath());
        }

        return CompletableFuture.completedFuture(updates);
    }

    private void deleteSourceFile(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
            log.info("[ParseResume] 已删除源文件: {}", filePath);
        } catch (IOException e) {
            log.warn("[ParseResume] 删除源文件失败: {}", filePath, e);
        }
    }

    private void updateResumeText(String sessionId, String resumeText) {
        try {
            ChatSession session = chatSessionMapper.selectOne(
                    new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
            if (session != null) {
                session.setResumeText(resumeText);
                chatSessionMapper.updateById(session);
            }
        } catch (Exception e) {
            log.warn("[ParseResume] 更新 chat_session.resumeText 失败, sessionId: {}", sessionId, e);
        }
    }
}
