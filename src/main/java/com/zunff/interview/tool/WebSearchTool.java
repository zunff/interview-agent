package com.zunff.interview.tool;

import com.zunff.interview.service.WebSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private final WebSearchService webSearchService;

    @Tool(description = "联网搜索，获取与关键词相关的网页信息。用于验证技术栈、查询公司背景、了解行业趋势等。")
    public String search(@ToolParam(description = "搜索关键词") String query) {
        log.info("[Tool] 联网搜索: {}", query);
        String result = webSearchService.search(query);
        return result != null ? result : "搜索失败或功能未启用";
    }
}