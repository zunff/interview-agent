package com.zunff.interview.agent.nodes;

import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 等待回答节点
 * 此节点会中断，等待外部调用 updateState 提交答案
 */
@Slf4j
@Component
public class WaitForAnswerNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("等待候选人回答问题 {}", state.questionIndex());
        return CompletableFuture.completedFuture(new HashMap<>());
    }
}
