package com.zunff.interview;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * 验证 Spring AI API 是否存在
 */
class APIVerificationTest {

    @Test
    void verifyChatResponseAPI() {
        ChatResponse response = new ChatResponse(null);

        // 验证 hasToolCalls() 方法存在
        boolean hasToolCalls = response.hasToolCalls();

        // 验证 getResult() 方法存在
        var result = response.getResult();

        if (result != null) {
            // 验证 getOutput() 方法存在
            var output = result.getOutput();

            if (output != null) {
                // 验证 AssistantMessage 的 getToolCalls() 方法存在
                if (output instanceof AssistantMessage assistantMessage) {
                    var toolCalls = assistantMessage.getToolCalls();

                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        var toolCall = toolCalls.get(0);

                        // 验证 ToolCall 的方法
                        String name = toolCall.name();
                        String arguments = toolCall.arguments();

                        System.out.println("Tool name: " + name);
                        System.out.println("Tool arguments: " + arguments);
                    }
                }
            }
        }
    }
}
