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
 * 软技能题生成节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftSkillGenNode {

    private final QuestionGenerationService questionGenerationService;

    public CompletableFuture<Map<String, Object>> execute(BatchQuestionGenState state) {
        try {
            int count = state.getSoftSkillCount();
            if (count <= 0) {
                log.info("软技能题数量为 0，跳过生成");
                return CompletableFuture.completedFuture(Map.of(
                        BatchQuestionGenState.SOFT_SKILL_QUESTIONS, new ArrayList<GeneratedQuestion>()
                ));
            }

            log.info("开始生成软技能题，数量: {}", count);

            return questionGenerationService.execute(state, count, QuestionType.COMMUNICATION, BatchQuestionGenState.SOFT_SKILL_QUESTIONS)
                    .thenApply(result -> {
                        @SuppressWarnings("unchecked")
                        List<GeneratedQuestion> questions = (List<GeneratedQuestion>) result.get(BatchQuestionGenState.SOFT_SKILL_QUESTIONS);
                        log.info("软技能题生成完成，实际生成: {}", questions != null ? questions.size() : 0);
                        return Map.of(BatchQuestionGenState.SOFT_SKILL_QUESTIONS, questions != null ? questions : new ArrayList<GeneratedQuestion>());
                    });

        } catch (Exception e) {
            log.error("软技能题生成失败", e);
            return CompletableFuture.completedFuture(Map.of(
                    BatchQuestionGenState.SOFT_SKILL_QUESTIONS, new ArrayList<GeneratedQuestion>()
            ));
        }
    }
}