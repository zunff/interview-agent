package com.zunff.interview.service.extend;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板管理服务
 * 统一管理和加载 prompt 模板文件
 *
 * 使用 '<' 和 '>' 作为变量分隔符，避免与 JSON 花括号冲突
 * 模板变量语法: <变量名>
 */
@Slf4j
@Service
public class PromptTemplateService {

    private static final String PROMPTS_DIR = "prompts/";
    private static final String PROMPT_EXTENSION = ".prompt";

    /** Prompt 缓存 */
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    /** 自定义渲染器：使用 <变量名> 语法 */
    private final StTemplateRenderer renderer = StTemplateRenderer.builder()
            .startDelimiterToken('<')
            .endDelimiterToken('>')
            .build();

    /**
     * 获取 prompt 模板（无变量替换）
     *
     * @param name 模板名称（不含扩展名）
     * @return prompt 内容
     */
    public String getPrompt(String name) {
        return promptCache.computeIfAbsent(name, this::loadPrompt);
    }

    /**
     * 获取 prompt 模板并进行变量替换
     *
     * @param name     模板名称（不含扩展名）
     * @param variables 变量映射
     * @return 替换后的 prompt 内容
     */
    public String getPrompt(String name, Map<String, Object> variables) {
        String template = getPrompt(name);
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        PromptTemplate promptTemplate = PromptTemplate.builder()
                .renderer(renderer)
                .template(template)
                .build();
        return promptTemplate.render(variables);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        promptCache.clear();
        log.info("Prompt cache cleared");
    }

    /**
     * 重新加载指定 prompt
     */
    public void reload(String name) {
        promptCache.remove(name);
        getPrompt(name);
        log.info("Prompt reloaded: {}", name);
    }

    private String loadPrompt(String name) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPTS_DIR + name + PROMPT_EXTENSION);
            if (!resource.exists()) {
                throw new RuntimeException("Prompt template not found: " + name);
            }
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded prompt template: {}", name);
            return content;
        } catch (IOException e) {
            log.error("Failed to load prompt: {}", name, e);
            throw new RuntimeException("Failed to load prompt: " + name, e);
        }
    }
}
