package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 候选人画像分析节点
 * 综合简历 + 自我介绍，一次 LLM 调用生成统一的候选人画像
 * 后续技术轮/业务轮的 QuestionGenerator 读取 candidateProfile 生成针对性问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileAnalysisNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成候选人画像");

        String resume = state.resume();
        String selfIntro = state.selfIntro();
        String jobInfo = state.jobInfo();

        if ((selfIntro == null || selfIntro.isEmpty()) && (resume == null || resume.isEmpty())) {
            log.warn("自我介绍和简历均为空，跳过画像分析");
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, "");
            updates.put(InterviewState.CANDIDATE_PROFILE, "");
            return CompletableFuture.completedFuture(updates);
        }

        String systemPrompt = promptTemplateService.getPrompt("profile-analysis");

        StringBuilder userPrompt = new StringBuilder();
        if (resume != null && !resume.isEmpty()) {
            userPrompt.append("候选人简历：\n").append(resume).append("\n\n");
        }
        if (selfIntro != null && !selfIntro.isEmpty()) {
            userPrompt.append("候选人自我介绍：\n").append(selfIntro).append("\n\n");
        }
        userPrompt.append("应聘岗位：\n").append(jobInfo).append("\n\n");
        userPrompt.append("请综合分析。");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            String candidateProfile = extractJson(response);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, selfIntro != null ? selfIntro : "");
            updates.put(InterviewState.CANDIDATE_PROFILE, candidateProfile);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("候选人画像生成完成，长度: {}", candidateProfile.length());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("候选人画像生成失败", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, selfIntro != null ? selfIntro : "");
            CircuitBreakerHelper.handleFailure(state, updates, e);
            return CompletableFuture.completedFuture(updates);
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
