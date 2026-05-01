package com.zunff.interview.agent.nodes.resume;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DimensionNodeHelper {

    private final ChatClient textChatClient;
    private final PromptTemplateService promptTemplateService;
    private final SseEmitterRegistry emitterRegistry;

    public CompletableFuture<Map<String, Object>> evaluate(ResumeState state,
                                                           String nodeKey,
                                                           String promptName,
                                                           String stateKey) {
        log.info("[{}] 开始评估, sessionId: {}", nodeKey, state.sessionId());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), nodeKey, "running");

        Map<String, Object> updates = new HashMap<>();
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("resumeText", state.resumeText());
            vars.put("companyInfo", state.companyInfo() != null ? state.companyInfo() : "");
            String promptText = promptTemplateService.getPrompt(promptName, vars);

            String result = textChatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            if (result == null) result = "";
            result = result.trim();

            if (result.startsWith("```")) {
                result = result.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JSONObject json = JSONUtil.parseObj(result);
            int score = json.getInt("score", 0);
            String comment = json.getStr("comment", "");
            String name = json.getStr("name", nodeKey);

            updates.put(stateKey, result);
            SseHelper.sendDimensionScore(emitterRegistry.get(state.sessionId()), nodeKey, name, score, comment);
            log.info("[{}] 评估完成, score: {}", nodeKey, score);
        } catch (Exception e) {
            log.error("[{}] 评估失败", nodeKey, e);
            updates.put(stateKey, "{\"name\":\"" + nodeKey + "\",\"key\":\"" + nodeKey + "\",\"score\":0,\"comment\":\"评估失败\"}");
        }

        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), nodeKey, "completed");
        return CompletableFuture.completedFuture(updates);
    }
}
