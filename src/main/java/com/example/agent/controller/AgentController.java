package com.example.agent.controller;

import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private CompiledGraph<?> graph;

    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, String> request) {
        String input = request.getOrDefault("input", "Hello");
        
        try {
            var result = graph.invoke(Map.of("input", input));
            return Map.of(
                "status", "success",
                "result", result.map(Object::toString).orElse("completed")
            );
        } catch (Exception e) {
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }
}
