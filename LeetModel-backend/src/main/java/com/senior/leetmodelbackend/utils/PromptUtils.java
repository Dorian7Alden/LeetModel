package com.senior.leetmodelbackend.utils;

import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.BasePrompt;
import com.senior.leetmodelbackend.pojo.enums.PromptEnums;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class PromptUtils {

    /**
     * 配置：是否优先从本地文件读取（true 则优先本地，false 则优先数据库）
     */
    private static final boolean PRIORITY_LOCAL = true;

    /**
     * 唯一的构建提示词的函数，支持传入封装好的提示词对象
     *
     * @param prompt 封装好的提示词对象
     * @return 构建好的完整提示词
     */
    public static String buildPrompt(BasePrompt prompt) {
        if (prompt == null) {
            return "";
        }
        return buildPrompt(prompt.getPromptEnum(), prompt.buildVariablesMap());
    }

    /**
     * 基础构建提示词的函数
     *
     * @param promptType 提示词类型
     * @param variablesMap 变量映射
     * @return 构建好的完整提示词
     */
    public static String buildPrompt(PromptEnums promptType, Map<String, String> variablesMap) {
        String template;

        if (PRIORITY_LOCAL) {
            template = loadTemplateFromLocal(promptType);
            if (template.isEmpty()) {
                template = loadTemplateFromDB(promptType);
            }
        } else {
            template = loadTemplateFromDB(promptType);
            if (template.isEmpty()) {
                template = loadTemplateFromLocal(promptType);
            }
        }

        return fillTemplateVariables(template, variablesMap);
    }

    /**
     * 从本地文件中读取提示词模板
     */
    private static String loadTemplateFromLocal(PromptEnums prompt) {
        String path = prompt.getPath();
        try (InputStream inputStream = PromptUtils.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                return "";
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                log.info("从本地文件加载提示词模板: {}", prompt.getDescription());
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            System.err.println("读取本地提示词模板失败: " + e.getMessage());
            return "";
        }
    }

    /**
     * 模拟从数据库读取提示词模板（仅供测试）
     */
    private static String loadTemplateFromDB(PromptEnums prompt) {
        // 模拟数据库返回，目前仅作示意
        // 如果是真实数据库，会根据 PromptEnums.name() 查询
        if (prompt == PromptEnums.GEN_FULL_PROBLEM_SYS_PROMPT) {
            log.info("从数据库中读取提示词模板: {}", prompt.getDescription());
            return "暂时没有实现从数据库读取提示词";
        }
        return "";
    }

    /**
     * 构建提示词的方法，将变量插入到模板中
     */
    private static String fillTemplateVariables(String template, Map<String, String> variablesMap) {
        if (template == null || template.isEmpty() || variablesMap == null) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, String> entry : variablesMap.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }


    /**
     * 解析提示词
     * 专门用来解析 ai 的响应，获取对应的数据
     */
    // TODO: 相应解析器

    


}