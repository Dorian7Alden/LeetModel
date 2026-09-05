package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3 大模型结构化输出鲁棒解析器。
 * 具备剥离 Markdown 代码围栏、提取首尾大括号、清洗 BOM 及宽容反序列化能力。
 */
public final class V3OutputParser {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private V3OutputParser() {}

    /**
     * 从大模型可能包含闲聊、代码围栏的输出中提取纯净的 JSON 文本。
     */
    public static String extractJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalArgumentException("模型输出为空，无法解析结构化结果");
        }
        String text = PromptTemplateRenderer.sanitize(rawOutput);

        // 1. 尝试剥离 ```json ... ``` 围栏
        Matcher matcher = CODE_FENCE_PATTERN.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).trim();
        }

        // 2. 截取首个 '{' 到最后一个 '}' 之间的闭包
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            text = text.substring(firstBrace, lastBrace + 1).trim();
        }

        return text;
    }

    /**
     * 宽容反序列化指定目标对象。
     */
    public static <T> T parse(ObjectMapper objectMapper, String rawOutput, Class<T> targetClass) throws Exception {
        String cleanJson = extractJson(rawOutput);
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        return lenientMapper.readValue(cleanJson, targetClass);
    }
}
