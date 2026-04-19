package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.constant.Difficulty;
import com.zunff.interview.constant.DifficultyPreference;
import com.zunff.interview.model.bo.JobAnalysisResult;
import com.zunff.interview.model.bo.LevelMatchResult;
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
 * 同时让 LLM 判断候选人级别和岗位匹配度，构建 LevelMatchResult
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

        // 获取岗位级别
        JobAnalysisResult.PositionLevel positionLevel = state.hasJobAnalysisResult()
                ? state.jobAnalysisResult().getPositionLevel()
                : JobAnalysisResult.PositionLevel.MID_LEVEL;

        if ((selfIntro == null || selfIntro.isEmpty()) && (resume == null || resume.isEmpty())) {
            log.warn("自我介绍和简历均为空，跳过画像分析");
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, "");
            updates.put(InterviewState.CANDIDATE_PROFILE, "");
            // 降级：使用默认级别匹配
            updates.put(InterviewState.LEVEL_MATCH_RESULT, buildDefaultLevelMatch(positionLevel));
            return CompletableFuture.completedFuture(updates);
        }

        String systemPrompt = promptTemplateService.getPrompt("profile-analysis");

        String userPrompt = promptTemplateService.getPrompt("profile-analysis-user", Map.of(
                "resume", resume == null ? "" : resume,
                "selfIntro", selfIntro == null ? "" : selfIntro,
                "jobInfo", jobContext == null ? "" : jobContext,
                "positionLevel", positionLevel.getDescription()
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

            // 从 LLM 返回构建 LevelMatchResult
            LevelMatchResult levelMatch = buildLevelMatchFromResponse(response, positionLevel);
            updates.put(InterviewState.LEVEL_MATCH_RESULT, levelMatch);

            log.info("级别匹配完成: positionLevel={}, candidateLevel={}, difficultyRange={}-{}, preference={}",
                    positionLevel.getDisplayName(),
                    levelMatch.candidateLevel().getDisplayName(),
                    levelMatch.difficultyRangeMin().getCode(),
                    levelMatch.difficultyRangeMax().getCode(),
                    levelMatch.difficultyPreference().getCode());

            CircuitBreakerHelper.recordSuccess(updates);

            log.info("候选人画像生成完成");

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("候选人画像生成失败", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.SELF_INTRO, selfIntro != null ? selfIntro : "");
            updates.put(InterviewState.LEVEL_MATCH_RESULT, buildDefaultLevelMatch(positionLevel));
            CircuitBreakerHelper.handleFailure(state, updates, e);
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 从 LLM 返回的 CandidateProfileResponseDto 构建 LevelMatchResult
     */
    private LevelMatchResult buildLevelMatchFromResponse(CandidateProfileResponseDto response, JobAnalysisResult.PositionLevel positionLevel) {
        // 候选人级别：LLM 返回 candidateLevelCode 优先，否则默认 MID_LEVEL
        JobAnalysisResult.PositionLevel candidateLevel;
        if (response.candidateLevelCode() != null) {
            candidateLevel = JobAnalysisResult.PositionLevel.fromCode(response.candidateLevelCode());
        } else {
            candidateLevel = JobAnalysisResult.PositionLevel.MID_LEVEL;
        }

        // 难度范围
        Difficulty difficultyRangeMin = Difficulty.fromCode(response.difficultyRangeMin());
        Difficulty difficultyRangeMax = Difficulty.fromCode(response.difficultyRangeMax());

        // 难度偏好
        DifficultyPreference difficultyPreference = DifficultyPreference.fromCode(response.difficultyPreference());

        return new LevelMatchResult(
                positionLevel,
                candidateLevel,
                difficultyRangeMin,
                difficultyRangeMax,
                difficultyPreference
        );
    }

    /**
     * 构建默认的 LevelMatchResult（降级场景）
     */
    private LevelMatchResult buildDefaultLevelMatch(JobAnalysisResult.PositionLevel positionLevel) {
        return new LevelMatchResult(
                positionLevel,
                JobAnalysisResult.PositionLevel.MID_LEVEL,
                Difficulty.EASY,
                Difficulty.HARD,
                DifficultyPreference.STANDARD
        );
    }
}
