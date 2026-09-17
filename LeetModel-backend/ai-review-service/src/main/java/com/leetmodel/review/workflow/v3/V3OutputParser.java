package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V3 大模型结构化输出鲁棒解析器。
 * 具备剥离 Markdown 代码围栏、提取首尾大括号、清洗 BOM 及宽容反序列化能力。
 */
public final class V3OutputParser {

    private static final Pattern CODE_FENCE_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Set<String> LATEX_COMMANDS_CONFLICTING_WITH_JSON_ESCAPES = Set.of(
            "bar",
            "begin",
            "beta",
            "frac",
            "nabla",
            "nu",
            "rho",
            "right",
            "tan",
            "text",
            "theta",
            "times"
    );

    private V3OutputParser() {}

    /**
     * 从大模型可能包含闲聊、代码围栏的输出中提取纯净的 JSON 文本。
     */
    public static String extractJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new IllegalArgumentException("模型输出为空，无法解析结构化结果");
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
        String cleanJson = repairInvalidJsonEscapes(extractJson(rawOutput));
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        JsonNode root = lenientMapper.readTree(cleanJson);
        normalizeSubTaskObservations(root, targetClass);
        return lenientMapper.treeToValue(root, targetClass);
    }

    /**
     * 将 JSON 字符串中的非法 LaTeX 反斜杠转义为普通反斜杠文本。
     *
     * @param json 待解析 JSON 文本，不能为 null
     * @return 保留合法 JSON 转义并修复非法转义后的文本
     */
    private static String repairInvalidJsonEscapes(String json) {
        StringBuilder repaired = new StringBuilder(json.length() + 32);
        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current != '\\' || index + 1 >= json.length()) {
                repaired.append(current);
                continue;
            }

            char escaped = json.charAt(index + 1);
            if (!isLatexCommandEscape(json, index)
                    && (isSimpleJsonEscape(escaped) || isValidUnicodeEscape(json, index))) {
                repaired.append(current).append(escaped);
                index++;
                continue;
            }
            repaired.append('\\').append('\\');
        }
        return repaired.toString();
    }

    /**
     * 判断反斜杠后是否为会与 JSON 控制转义冲突的常见 LaTeX 命令。
     *
     * @param json JSON 文本
     * @param slashIndex 反斜杠位置
     * @return 命中冲突命令时返回 true
     */
    private static boolean isLatexCommandEscape(String json, int slashIndex) {
        int commandStart = slashIndex + 1;
        int commandEnd = commandStart;
        while (commandEnd < json.length() && Character.isLetter(json.charAt(commandEnd))) {
            commandEnd++;
        }
        if (commandEnd == commandStart) return false;
        String command = json.substring(commandStart, commandEnd);
        return LATEX_COMMANDS_CONFLICTING_WITH_JSON_ESCAPES.contains(command);
    }

    /**
     * 判断当前字符是否属于 JSON 允许的单字符转义。
     *
     * @param escaped 反斜杠后的字符
     * @return 属于合法单字符转义时返回 true
     */
    private static boolean isSimpleJsonEscape(char escaped) {
        return escaped == '"'
                || escaped == '\\'
                || escaped == '/'
                || escaped == 'b'
                || escaped == 'f'
                || escaped == 'n'
                || escaped == 'r'
                || escaped == 't';
    }

    /**
     * 判断当前位置是否以合法的四位十六进制 Unicode 转义开头。
     *
     * @param json JSON 文本
     * @param slashIndex 反斜杠位置
     * @return 后续内容为合法 Unicode 转义时返回 true
     */
    private static boolean isValidUnicodeEscape(String json, int slashIndex) {
        if (slashIndex + 5 >= json.length() || json.charAt(slashIndex + 1) != 'u') {
            return false;
        }
        for (int index = slashIndex + 2; index <= slashIndex + 5; index++) {
            if (Character.digit(json.charAt(index), 16) < 0) return false;
        }
        return true;
    }

    private static <T> void normalizeSubTaskObservations(
            JsonNode root,
            Class<T> targetClass
    ) {
        if (targetClass != SubTaskEvaluationResultDTO.class || !(root instanceof ObjectNode object)) {
            return;
        }
        JsonNode observations = object.get("observations");
        if (!(observations instanceof ArrayNode source)) return;

        ArrayNode normalized = object.arrayNode();
        int sequence = 1;
        for (JsonNode observation : source) {
            if (!observation.isTextual()) {
                normalized.add(observation);
                continue;
            }
            ObjectNode item = object.objectNode();
            item.put("observationId", "OBS_MODEL_" + sequence++);
            item.put("observationType", "MODEL_SUMMARY");
            item.put("summary", observation.asText());
            normalized.add(item);
        }
        object.set("observations", normalized);
    }
}
