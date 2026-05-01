package com.zunff.interview.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FinishChatTool {

    @Tool(description = "当思考完成时调用此工具，传递思考总结和关键信息，用于生成最终回答")
    public String finishChat(
            @ToolParam(description = "思考过程的总结，包含关键信息和结论") String summary,
            @ToolParam(description = "关键信息点列表，JSON 数组格式，例如：['Spring AI 2.x 发布', '新增工具方法识别']", required = false) String keyPoints
    ) {
        log.info("[Tool] 思考完成，summary: {}", summary);
        return "思考已完成，总结: " + summary;
    }
}