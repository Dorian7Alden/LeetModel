package com.leetmodel.knowledge.defense;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.knowledge.defense.dto.CatalogSelectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 防线 1 & 防线 3: 防御性 JSON 解析器。
 * 遵循 docs/learning/提示词管理.md：
 * 1. 自动剥离 Markdown 代码围栏；
 * 2. 贪婪截取首个 { 与最后一个 } 闭包；
 * 3. 容错修复 LaTeX 公式反斜杠（如 \\frac、\\alpha），避免破坏 JSON 转义；
 * 4. 宽容反序列化。
 */
@Slf4j
@Component
public class DefensiveCatalogOutputParser {

    private static final Pattern INVALID_JSON_ESCAPE =
            Pattern.compile("\\\\(?![\\\\\"/bfnrt]|u[0-9a-fA-F]{4})");

    private final ObjectMapper objectMapper;

    public DefensiveCatalogOutputParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    public CatalogSelectionResponse parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            log.warn("选文模型输出为空，无法解析");
            return new CatalogSelectionResponse("", java.util.List.of());
        }

        // 1. 清洗控制字符与 BOM
        String sanitized = sanitizeControlCharacters(rawOutput);

        // 2. 剥离 Markdown 代码围栏
        String stripped = stripCodeFences(sanitized);

        // 3. 贪婪截取首尾大括号闭包
        String jsonPayload = extractJsonClosure(stripped);

        // 4. 容错修复 LaTeX 反斜杠
        String repairedJson = repairBackslashes(jsonPayload);

        // 5. 反序列化
        try {
            return objectMapper.readValue(repairedJson, CatalogSelectionResponse.class);
        } catch (Exception e) {
            log.warn("防御性 JSON 解析失败: rawLength={}, error={}", rawOutput.length(), e.getMessage());
            throw new IllegalArgumentException("无法反序列化模型选拔输出: " + e.getMessage(), e);
        }
    }

    private String sanitizeControlCharacters(String text) {
        return text.replace("\uFEFF", "")
                .replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "")
                .trim();
    }

    private String stripCodeFences(String text) {
        String result = text.trim();
        if (result.startsWith("```")) {
            int newline = result.indexOf('\n');
            if (newline >= 0) {
                result = result.substring(newline + 1);
            }
            int lastFence = result.lastIndexOf("```");
            if (lastFence >= 0) {
                result = result.substring(0, lastFence);
            }
        }
        return result.trim();
    }

    private String extractJsonClosure(String text) {
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1);
        }
        return text;
    }

    private String repairBackslashes(String text) {
        return INVALID_JSON_ESCAPE.matcher(text).replaceAll("\\\\\\\\");
    }
}
