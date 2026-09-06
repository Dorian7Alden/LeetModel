package com.leetmodel.suggestion.workflow.v3;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3 建议大模型结构化输出鲁棒解析器。
 * 严格对齐 docs/learning/提示词管理.md 规范：
 * 1. 清洗不可见字符与 BOM；
 * 2. 剥离 Markdown 代码围栏；
 * 3. 贪婪截取首尾大括号或中括号闭包；
 * 4. 宽容反序列化：允许 LaTeX 任意字符反斜杠转义、允许未转义控制字符、忽略未知字段。
 */
public final class V3OutputParser {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private V3OutputParser() {}

    /**
     * 从大模型输出中剥离闲聊废话、代码围栏，截取纯净的 JSON 文本。
     */
    public static String extractJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalArgumentException("模型输出为空，无法解析结构化建议");
        }
        String text = PromptTemplateRenderer.sanitize(rawOutput);

        // 1. 尝试剥离外层 ```json ... ``` 代码围栏 (仅当文本以 ``` 开头时剥离，避免误伤 JSON 字符串内部嵌套的 Markdown 代码块)
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // 2. 截取首个 '{' 或 '[' 闭包
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');

        if (firstBrace >= 0 && (firstBracket < 0 || firstBrace < firstBracket)) {
            if (lastBrace > firstBrace) {
                text = text.substring(firstBrace, lastBrace + 1).trim();
            }
        } else if (firstBracket >= 0 && lastBracket > firstBracket) {
            text = text.substring(firstBracket, lastBracket + 1).trim();
        }

        return text;
    }

    /**
     * 宽容反序列化指定目标对象，杜绝常见 LaTeX 反斜杠转义崩溃。
     */
    public static <T> T parse(ObjectMapper objectMapper, String rawOutput, Class<T> targetClass) throws Exception {
        String cleanJson = extractJson(rawOutput);
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
                .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        return lenientMapper.readValue(cleanJson, targetClass);
    }
}
