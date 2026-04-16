package com.zunff.interview.agent.nodes.question.gen;

import com.zunff.interview.agent.state.BatchQuestionGenState;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.service.interview.QuestionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 技术基础题生成节点
 * 独立节点，负责生成技术基础题目
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechBasicGenNode {

    private final QuestionGenerationService questionGenerationService;

    /**
     * 执行技术基础题生成
     */
    public CompletableFuture<Map<String, Object>> execute(BatchQuestionGenState state) {
        try {
            int count = state.getTechnicalBasicCount();
            if (count <= 0) {
                log.info("技术基础题数量为 0，跳过生成");
                return CompletableFuture.completedFuture(Map.of(
                        BatchQuestionGenState.TECHNICAL_BASIC_QUESTIONS, new ArrayList<GeneratedQuestion>()
                ));
            }

            log.info("开始生成技术基础题，数量: {}", count);

            // 调用题目生成服务
            return questionGenerationService.execute(state, count, QuestionType.TECHNICAL_BASIC, BatchQuestionGenState.TECHNICAL_BASIC_QUESTIONS)
                    .thenApply(result -> {
                        @SuppressWarnings("unchecked")
                        List<GeneratedQuestion> questions = (List<GeneratedQuestion>) result.get(BatchQuestionGenState.TECHNICAL_BASIC_QUESTIONS);
                        log.info("技术基础题生成完成，实际生成: {}", questions != null ? questions.size() : 0);
                        return Map.of(BatchQuestionGenState.TECHNICAL_BASIC_QUESTIONS, questions != null ? questions : new ArrayList<GeneratedQuestion>());
                    });

        } catch (Exception e) {
            log.error("技术基础题生成失败", e);
            return CompletableFuture.completedFuture(Map.of(
                    BatchQuestionGenState.TECHNICAL_BASIC_QUESTIONS, new ArrayList<GeneratedQuestion>()
            ));
        }
    }
}
