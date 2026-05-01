package com.zunff.interview.agent.nodes.resume;

import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadGuardNode {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "doc", "txt");
    private final SseEmitterRegistry emitterRegistry;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        log.info("[UploadGuard] 校验文件, sessionId: {}", state.sessionId());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "upload", "running");

        Map<String, Object> updates = new HashMap<>();
        String filePath = state.filePath();
        String fileName = state.fileName();

        if (filePath == null || filePath.isEmpty()) {
            updates.put(ResumeState.ERROR, "文件路径为空");
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "upload", "failed");
            return CompletableFuture.completedFuture(updates);
        }

        File file = new File(filePath);
        if (!file.exists()) {
            updates.put(ResumeState.ERROR, "文件不存在: " + filePath);
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "upload", "failed");
            return CompletableFuture.completedFuture(updates);
        }

        String extension = getFileExtension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension.toLowerCase())) {
            updates.put(ResumeState.ERROR, "不支持的文件类型: " + extension);
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "upload", "failed");
            return CompletableFuture.completedFuture(updates);
        }

        updates.put(ResumeState.FILE_TYPE, extension.toLowerCase());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "upload", "completed");
        log.info("[UploadGuard] 文件校验通过: {}", fileName);
        return CompletableFuture.completedFuture(updates);
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
}
