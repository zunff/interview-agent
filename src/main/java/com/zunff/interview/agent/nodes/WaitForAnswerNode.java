package com.zunff.interview.agent.nodes;

import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptableAction;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 等待回答节点
 * 此节点会中断流程，等待外部调用 submitAnswer 提交答案后恢复
 */
@Slf4j
@Component
public class WaitForAnswerNode implements InterruptableAction<InterviewState> {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("等待候选人回答问题 {}", state.questionIndex());
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    @Override
    public Optional<InterruptionMetadata<InterviewState>> interrupt(String nodeId, InterviewState state, RunnableConfig config) {
        // 始终中断，等待外部输入
        log.info("中断流程，等待回答: nodeId={}, questionIndex={}", nodeId, state.questionIndex());
        return Optional.of(InterruptionMetadata.<InterviewState>builder(nodeId, state)
                .putMetadata("waiting_for", "answer")
                .putMetadata("question_index", state.questionIndex())
                .build());
    }
}
