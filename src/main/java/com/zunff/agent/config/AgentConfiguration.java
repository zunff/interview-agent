package com.zunff.agent.config;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Configuration
public class AgentConfiguration {

    public static class SimpleState extends AgentState {
        public static final String MESSAGES_KEY = "messages";
        
        public static final Map<String, Channel<?>> SCHEMA = new HashMap<>();
        static {
            SCHEMA.put(MESSAGES_KEY, Channels.appender(ArrayList::new));
        }

        public SimpleState(Map<String, Object> initData) {
            super(initData);
        }
    }

    @Bean
    public CompiledGraph<SimpleState> simpleGraph(ChatModel chatModel) throws GraphStateException {
        return new StateGraph<>(SimpleState.SCHEMA, SimpleState::new)
            .addNode("start", (state, config) -> {
                Map<String, Object> result = new HashMap<>();
                result.put(SimpleState.MESSAGES_KEY, "Starting...");
                return CompletableFuture.completedFuture(result);
            })
            .addEdge(START, "start")
            .addEdge("start", END)
            .compile();
    }
}
