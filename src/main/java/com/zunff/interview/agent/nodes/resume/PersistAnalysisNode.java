package com.zunff.interview.agent.nodes.resume;

import cn.hutool.core.util.IdUtil;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import com.zunff.interview.mapper.ResumeAnalysisMapper;
import com.zunff.interview.model.entity.ResumeAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersistAnalysisNode {

    private final ResumeAnalysisMapper resumeAnalysisMapper;
    private final SseEmitterRegistry emitterRegistry;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        log.info("[PersistAnalysis] 持久化分析结果, sessionId: {}", state.sessionId());

        Map<String, Object> updates = new HashMap<>();
        String error = state.error();
        if (error != null && !error.isEmpty()) {
            SseHelper.sendError(emitterRegistry.get(state.sessionId()), error);
            emitterRegistry.remove(state.sessionId());
            return CompletableFuture.completedFuture(updates);
        }

        String analysisId = IdUtil.simpleUUID().substring(0, 16);
        try {
            String radarJson = state.radarChartJson();
            String reportMd = state.reportMarkdown();

            ResumeAnalysis analysis = ResumeAnalysis.builder()
                    .analysisId(analysisId)
                    .sessionId(state.sessionId())
                    .resumeText(state.resumeText())
                    .reportMarkdown(reportMd != null && !reportMd.isEmpty() ? reportMd : null)
                    .radarChartData(radarJson != null && !radarJson.isEmpty() ? radarJson : "{}")
                    .status(ResumeAnalysis.Status.DONE.name())
                    .createTime(LocalDateTime.now())
                    .build();

            resumeAnalysisMapper.insert(analysis);
            updates.put(ResumeState.ANALYSIS_ID, analysisId);
            log.info("[PersistAnalysis] 分析结果已保存, analysisId: {}", analysisId);
        } catch (Exception e) {
            log.error("[PersistAnalysis] 保存分析结果失败", e);
        }

        SseHelper.sendDone(emitterRegistry.get(state.sessionId()));
        emitterRegistry.remove(state.sessionId());
        return CompletableFuture.completedFuture(updates);
    }
}
