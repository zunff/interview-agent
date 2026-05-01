package com.zunff.interview.agent.nodes.resume;

import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.common.sse.SseEmitterRegistry;
import com.zunff.interview.common.sse.SseHelper;
import com.zunff.interview.mapper.CompanyMapper;
import com.zunff.interview.model.entity.Company;
import com.zunff.interview.service.WebSearchService;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyResearchNode {

    private final ChatClient textChatClient;
    private final PromptTemplateService promptTemplateService;
    private final SseEmitterRegistry emitterRegistry;
    private final WebSearchService webSearchService;
    private final CompanyMapper companyMapper;

    @Value("${company.cache.ttl-days:30}")
    private int cacheTtlDays;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        log.info("[CompanyResearch] 公司背景调研, sessionId: {}", state.sessionId());
        SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "company", "running");

        Map<String, Object> updates = new HashMap<>();
        try {
            // 1. 让 LLM 从简历中提取公司名列表
            String extractPrompt = """
                    从以下简历中提取候选人工作过的所有公司名称，每行一个，只输出公司名，不要其他内容。
                    如果无法识别公司名，输出"未知"。

                    简历内容：
                    %s
                    """.formatted(truncate(state.resumeText(), 3000));

            String companyListText = textChatClient.prompt()
                    .user(extractPrompt)
                    .call()
                    .content();
            if (companyListText == null) companyListText = "";

            // 2. 解析公司名列表
            List<String> companyNames = new ArrayList<>();
            for (String name : companyListText.split("\n")) {
                name = name.trim().replaceAll("^[\\d\\-.*]+", "").trim();
                if (!name.isEmpty() && !name.equals("未知")) {
                    companyNames.add(name);
                }
            }

            // 3. 批量查询缓存
            Map<String, Company> cachedMap = new HashMap<>();
            if (!companyNames.isEmpty()) {
                List<Company> cached = companyMapper.findActiveByNames(companyNames, LocalDateTime.now());
                for (Company c : cached) {
                    cachedMap.put(c.getCompanyName(), c);
                    companyMapper.incrementHitCount(c.getId(), LocalDateTime.now());
                    log.info("[CompanyResearch] 缓存命中: {}", c.getCompanyName());
                }
            }

            // 4. 逐个处理公司（优先缓存，未命中则搜索并入库）
            StringBuilder searchContext = new StringBuilder();
            for (String companyName : companyNames) {
                Company cached = cachedMap.get(companyName);
                if (cached != null && cached.getAnalyzedResult() != null) {
                    searchContext.append("### ").append(companyName).append("\n");
                    searchContext.append(cached.getAnalyzedResult()).append("\n\n");
                } else {
                    log.info("[CompanyResearch] 搜索公司: {}", companyName);
                    String searchResult = webSearchService.search(companyName + " 公司简介 行业 规模");

                    if (searchResult != null) {
                        searchContext.append("### ").append(companyName).append("\n");
                        searchContext.append(searchResult).append("\n\n");
                        saveToCache(companyName, searchResult);
                    }
                }
            }

            // 5. 结合搜索结果让 LLM 总结
            String promptText = promptTemplateService.getPrompt("resume-company-research", Map.of(
                    "resumeText", truncate(state.resumeText(), 3000),
                    "searchResults", !searchContext.isEmpty() ? searchContext.toString() : "（无搜索结果，请根据自身知识分析）"
            ));

            String result = textChatClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
            if (result == null) result = "";

            updates.put(ResumeState.COMPANY_INFO, result);
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "company", "completed");
            log.info("[CompanyResearch] 调研完成, 长度: {}", result.length());
        } catch (Exception e) {
            log.error("[CompanyResearch] 调研失败", e);
            updates.put(ResumeState.COMPANY_INFO, "");
            SseHelper.sendProgress(emitterRegistry.get(state.sessionId()), "company", "completed");
        }

        return CompletableFuture.completedFuture(updates);
    }

    private void saveToCache(String companyName, String rawSearchResult) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Company existing = companyMapper.findActiveByName(companyName, now);

            if (existing != null) {
                existing.setRawSearchResult(rawSearchResult);
                existing.setAnalyzedResult(rawSearchResult);
                existing.setUpdateTime(now);
                existing.setExpiresAt(now.plusDays(cacheTtlDays));
                companyMapper.updateById(existing);
            } else {
                Company company = Company.builder()
                        .companyName(companyName)
                        .rawSearchResult(rawSearchResult)
                        .analyzedResult(rawSearchResult)
                        .expiresAt(now.plusDays(cacheTtlDays))
                        .hitCount(0)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                companyMapper.insert(company);
            }
            log.info("[CompanyResearch] 已缓存公司: {}", companyName);
        } catch (Exception e) {
            log.warn("[CompanyResearch] 缓存公司失败: {}", companyName, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
