package com.zunff.interview.agent.nodes.question.gen;

import com.zunff.interview.agent.state.BatchQuestionGenState;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.service.interview.QuestionGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 业务理解题生成节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessGenNode {

    private final QuestionGenerationService questionGenerationService;

    public CompletableFuture<Map<String, Object>> execute(BatchQuestionGenState state) {
        try {
            int count = state.getBusinessCount();
            if (count <= 0) {
                log.info("业务理解题数量为 0，跳过生成");
                return CompletableFuture.completedFuture(Map.of(
                        BatchQuestionGenState.BUSINESS_QUESTIONS, new ArrayList<GeneratedQuestion>()
                ));
            }

            log.info("开始生成业务理解题，数量: {}", count);

            return questionGenerationService.execute(state, count, QuestionType.BUSINESS_UNDERSTANDING, BatchQuestionGenState.BUSINESS_QUESTIONS)
                    .thenApply(result -> {
                        @SuppressWarnings("unchecked")
                        List<GeneratedQuestion> questions = (List<GeneratedQuestion>) result.get(BatchQuestionGenState.BUSINESS_QUESTIONS);
                        log.info("业务理解题生成完成，实际生成: {}", questions != null ? questions.size() : 0);
                        return Map.of(BatchQuestionGenState.BUSINESS_QUESTIONS, questions != null ? questions : new ArrayList<GeneratedQuestion>()
                        );
                    });

        } catch (Exception e) {
            log.error("业务理解题生成失败", e);
            return CompletableFuture.completedFuture(Map.of(
                    BatchQuestionGenState.BUSINESS_QUESTIONS, new ArrayList<GeneratedQuestion>()
            ));
        }
    }
}