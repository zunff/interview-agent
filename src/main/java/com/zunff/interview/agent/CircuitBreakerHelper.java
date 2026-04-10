package com.zunff.interview.agent;

import com.zunff.interview.state.InterviewState;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断器工具类
 * 统一管理 LLM 调用的失败计数和熔断判断，供所有 Node 复用
 */
public final class CircuitBreakerHelper {

    private CircuitBreakerHelper() {
    }

    /**
     * LLM 调用成功时调用：重置失败计数为 0
     */
    public static void recordSuccess(Map<String, Object> updates) {
        updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, 0);
    }

    /**
     * LLM 调用失败时调用：递增失败计数到已有 map 中，达到阈值抛异常触发熔断
     */
    public static void handleFailure(InterviewState state, Map<String, Object> updates, Exception e) {
        int failures = state.consecutiveLLMFailures() + 1;
        if (failures >= state.maxLLMFailures()) {
            throw new RuntimeException(
                    "LLM 连续调用失败达到 " + state.maxLLMFailures() + " 次，触发熔断", e);
        }
        updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, failures);
    }
}
