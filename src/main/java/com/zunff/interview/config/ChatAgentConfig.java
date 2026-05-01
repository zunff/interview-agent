package com.zunff.interview.config;

import com.zunff.interview.tool.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ChatAgentConfig {

    @Bean
    public ChatMemory resumeChatMemory(ChatMemoryRepository chatMemoryRepository) {
        log.info("初始化聊天记忆（JDBC 持久化，repository: {}）", chatMemoryRepository.getClass().getSimpleName());
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ToolCallbackProvider resumeAnalysisTools(WebSearchTool webSearchTool) {
        log.info("注册简历分析工具: webSearch");
        return MethodToolCallbackProvider.builder()
                .toolObjects(webSearchTool)
                .build();
    }
}
