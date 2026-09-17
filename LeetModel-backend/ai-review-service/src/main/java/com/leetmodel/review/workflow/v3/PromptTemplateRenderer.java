package com.leetmodel.review.workflow.v3;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 提示词安全渲染引擎。
 * 严格遵循 docs/learning/提示词管理.md 规范：
 * 1. 强制使用 [[ ]] 自定义占位符定界符，杜绝与 LaTeX {} 及 HTML <table> 的花括号冲突；
 * 2. 采用确定性子串定位与替换，杜绝 Matcher.replaceAll 对 $1 与反斜杠 \ 的正则语法误伤；
 * 3. 严格清洗 BOM 与不可见非法控制字符。
 */
public final class PromptTemplateRenderer {

    private PromptTemplateRenderer() {}

    /**
     * 从类路径加载模板文件内容。
     */
    public static String loadClasspathPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载提示词模板: " + path, exception);
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
     * 使用自定义 [[key]] 定界符对模板进行安全纯文本替换。
     * 不使用正则引擎，因此变量值中含 $、\、{} 时 100% 安全自洽。
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
