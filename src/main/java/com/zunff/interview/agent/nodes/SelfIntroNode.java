package com.zunff.interview.agent.nodes;

import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 自我介绍等待节点
 * 只设置 QUESTION_TYPE 标记当前阶段，前端自行引导用户进行自我介绍
 * 通过 interrupt 机制暂停图执行，等待用户回答后恢复
 */
@Slf4j
@Component
public class SelfIntroNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("进入自我介绍阶段，等待候选人回答");

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTION_TYPE, "自我介绍");

        return CompletableFuture.completedFuture(updates);
    }
}
