package com.zunff.interview.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class WebSearchService {

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final String apiKey;
    private final boolean enabled;
    private final RestClient restClient;

    public WebSearchService(@Value("${search.tavily.api-key:}") String apiKey,
                            @Value("${search.tavily.enabled:false}") boolean enabled) {
        this.apiKey = apiKey;
        this.enabled = enabled;
        this.restClient = RestClient.builder().baseUrl(TAVILY_API_URL).build();
    }

    public String search(String query) {
        log.info("联网搜索: {}", query);

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("联网搜索功能未启用（缺少 TAVILY_API_KEY）");
            return null;
        }

        try {
            String body = JSONUtil.createObj()
                    .set("api_key", apiKey)
                    .set("query", query)
                    .set("search_depth", "basic")
                    .set("max_results", 5)
                    .set("include_answer", true)
                    .toString();

            String response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            String summary = extractSummary(response);
            log.info("搜索完成, 结果长度: {}", summary.length());
            return summary;
        } catch (Exception e) {
            log.error("联网搜索失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private String extractSummary(String response) {
        try {
            JSONObject json = JSONUtil.parseObj(response);

            StringBuilder sb = new StringBuilder();

            String answer = json.getStr("answer");
            if (answer != null && !answer.isBlank()) {
                sb.append(answer).append("\n\n");
            }

            JSONArray results = json.getJSONArray("results");
            if (results != null) {
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    sb.append("- ").append(item.getStr("title", "")).append("\n");
                    String content = item.getStr("content", "");
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    sb.append("  ").append(content).append("\n");
                    sb.append("  ").append(item.getStr("url", "")).append("\n\n");
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("解析搜索结果失败，返回原始响应", e);
            return response;
        }
    }
}