package com.leetmodel.knowledge.prompt;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 提示词安全渲染引擎。
 * 遵循 docs/learning/提示词管理.md 规范：
 * 1. 采用 [[ ]] 占位符，隔绝与 JSON 花括号和 LaTeX 反斜杠/大括号冲突；
 * 2. 采用确定性子串字面量替换，避免 Matcher.replaceAll 对 $ 与 \\ 的正则特殊语义破坏；
 * 3. 严格清洗 BOM 与不可见非法控制字符。
 */
public final class PromptTemplateRenderer {

    private PromptTemplateRenderer() {}

    /**
     * 从类路径加载提示词模板。
     */
    public static String loadClasspathPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法加载提示词模板: " + path, e);
        }
    }

    /**
     * 清洗非法控制字符与 BOM，保留普通换行符与制表符。
     */
    public static String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\uFEFF", "")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "")
                .trim();
    }

    /**
     * 使用自定义 [[key]] 定界符对模板进行安全纯文本字面量替换。
     */
    public static String render(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) return "";
        if (variables == null || variables.isEmpty()) return template;

        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "[[" + entry.getKey() + "]]";
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = replaceLiteral(result, placeholder, value);
        }
        return result;
    }

    private static String replaceLiteral(String source, String target, String replacement) {
        int start = source.indexOf(target);
        if (start < 0) return source;
        StringBuilder sb = new StringBuilder(source.length() + replacement.length() - target.length());
        int cursor = 0;
        while (start >= 0) {
            sb.append(source, cursor, start);
            sb.append(replacement);
            cursor = start + target.length();
            start = source.indexOf(target, cursor);
        }
        sb.append(source, cursor, source.length());
        return sb.toString();
    }
}
