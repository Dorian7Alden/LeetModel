package com.senior.leetmodelbackend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PromptService {

    private static final String SYSTEM_PROMPT_PATH = "prompts/review-system-prompt.st";
    private static final String USER_PROMPT_PATH = "prompts/review-user-prompt.st";

    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadPrompts() {
        loadTemplate("system", SYSTEM_PROMPT_PATH);
        loadTemplate("user", USER_PROMPT_PATH);
        log.info("提示词模板加载完成，共 {} 个", templateCache.size());
    }

    private void loadTemplate(String key, String path) {
        try {
            Resource resource = new ClassPathResource(path);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            templateCache.put(key, new PromptTemplate(content));
            log.info("加载提示词模板: {}", path);
        } catch (Exception e) {
            log.error("加载提示词模板失败: {}", path, e);
            throw new RuntimeException("加载提示词模板失败: " + path, e);
        }
    }

    public SystemMessage buildSystemMessage(String dimensionName) {
        PromptTemplate template = templateCache.get("system");
        if (template == null) {
            throw new IllegalStateException("系统提示词模板未加载");
        }
        return new SystemMessage(template.render(Map.of("dimensionName", dimensionName)));
    }

    public UserMessage buildUserMessage(String problemContent, String submissionContent) {
        PromptTemplate template = templateCache.get("user");
        if (template == null) {
            throw new IllegalStateException("用户提示词模板未加载");
        }
        return new UserMessage(template.render(Map.of(
                "problemContent", problemContent != null ? problemContent : "",
                "submissionContent", submissionContent != null ? submissionContent : ""
        )));
    }
}
