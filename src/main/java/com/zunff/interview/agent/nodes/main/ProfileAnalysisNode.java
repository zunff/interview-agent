package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.llm.resp.CandidateProfileResponseDto;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.InterviewState;
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

        // 优先使用岗位分析结果，否则降级使用原始岗位信息
        String jobContext = state.hasJobAnalysisResult()
                ? state.jobAnalysisResult().generateJobSummary()
                : state.jobInfo();

        if ((selfIntro == null || selfIntro.isEmpty()) && (resume == null || resume.isEmpty())) {
            log.warn("自我介绍和简历均为空，跳过画像分析");
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, "");
            updates.put(InterviewState.CANDIDATE_PROFILE, "");
            return CompletableFuture.completedFuture(updates);
        }

        String systemPrompt = promptTemplateService.getPrompt("profile-analysis");

        String userPrompt = promptTemplateService.getPrompt("profile-analysis-user", Map.of(
                "resume", resume == null ? "" : resume,
                "selfIntro", selfIntro == null ? "" : selfIntro,
                "jobInfo", jobContext == null ? "" : jobContext
        ));

        try {
            ChatClient chatClient = chatClientBuilder.build();

            CandidateProfileResponseDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(CandidateProfileResponseDto.class);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, selfIntro != null ? selfIntro : "");
            updates.put(InterviewState.CANDIDATE_PROFILE, response);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("候选人画像生成完成");

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("候选人画像生成失败", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, selfIntro != null ? selfIntro : "");
            CircuitBreakerHelper.handleFailure(state, updates, e);
            return CompletableFuture.completedFuture(updates);
        }
    }

}
