package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.analysis.VisionAnalysisResult;
import com.zunff.interview.service.MultimodalAnalysisService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 视觉分析节点
 * 分析候选人的视频帧（表情、肢体语言）
 * 作为并行分析分支的一部分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionAnalysisNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    /**
     * 执行视觉帧分析
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始视觉帧分析");

        @SuppressWarnings("unchecked")
        List<String> answerFrames = (List<String>) state.data().getOrDefault(
                InterviewState.ANSWER_FRAMES,
                List.of()
        );

        try {
            VisionAnalysisResult visionResult = multimodalAnalysisService.analyzeVideoFrames(answerFrames);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.VISION_ANALYSIS_RESULT, visionResult);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("视觉帧分析完成，表情得分: {}, 肢体语言得分: {}",
                    visionResult.getEmotionScore(), visionResult.getBodyLanguageScore());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("视觉帧分析失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 返回默认结果，继续流转
            updates.put(InterviewState.VISION_ANALYSIS_RESULT, VisionAnalysisResult.defaultResult());
            return CompletableFuture.completedFuture(updates);
        }
    }
}
