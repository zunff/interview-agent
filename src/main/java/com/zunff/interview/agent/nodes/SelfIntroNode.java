package com.zunff.interview.agent.nodes;

import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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

    private final InterviewWebSocketHandler webSocketHandler;

    public SelfIntroNode(@Lazy InterviewWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }


    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("进入自我介绍阶段，等待候选人回答");

        // 让前端开始自我介绍阶段：只发信号，前端自行引导
        webSocketHandler.sendMessage(state.sessionId(), WebSocketMessage.of(WebSocketMessage.Type.SELF_INTRO, Map.of()));

        return CompletableFuture.completedFuture(new HashMap<>());
    }
}
