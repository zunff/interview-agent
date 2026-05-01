package com.zunff.interview.agent.nodes.resume;

import com.zunff.interview.agent.state.ResumeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BgDimensionNode {

    private final DimensionNodeHelper helper;

    public CompletableFuture<Map<String, Object>> execute(ResumeState state) {
        return helper.evaluate(state, "bg", "resume-bg-dimension", ResumeState.BG_SCORE);
    }
}
