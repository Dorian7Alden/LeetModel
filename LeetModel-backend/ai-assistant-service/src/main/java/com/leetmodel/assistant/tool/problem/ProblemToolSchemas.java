package com.leetmodel.assistant.tool.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首版题目工具的不可变 JSON Schema。 */
final class ProblemToolSchemas {

    private ProblemToolSchemas() {
    }

    /** 构造 search_problem 输入 Schema。 */
    static Map<String, Object> search() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("code", integer(1001, 10000, "精确题号，与 keyword 二选一"));
        properties.put("keyword", string(1, 50, null, "题目标题关键词，与 code 二选一"));
        properties.put("includeOverview", Map.of("type", "boolean",
                "description", "用户明确询问题面时为 true，默认 false"));
        properties.put("limit", integer(1, 5, "最大返回数，默认 5；题号查询固定为 1"));
        Map<String, Object> schema = objectSchema(properties);
        schema.put("oneOf", List.of(
                Map.of("required", List.of("code"), "not", Map.of("required", List.of("keyword"))),
                Map.of("required", List.of("keyword"), "not", Map.of("required", List.of("code")))));
        return schema;
    }

    /** 构造 recommend_problem 输入 Schema。 */
    static Map<String, Object> recommend() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", string(1, 50, null, "可选的标题或标签关键词"));
        properties.put("contestCode", string(null, 32, "^[A-Z][A-Z0-9_]{0,31}$", "可选赛事编码"));
        properties.put("year", integer(2000, 2100, "可选题目年份"));
        properties.put("difficulty", integer(1, 3, "可选难度：1、2、3"));
        properties.put("statementLanguage", Map.of("type", "string",
                "enum", List.of("ZH", "EN"), "description", "可选题面语言"));
        properties.put("maxDurationMinutes", integer(30, 10080, "可选最大建议完成时长"));
        properties.put("limit", integer(1, 5, "最大返回数，默认 3"));
        return objectSchema(properties);
    }

    /** 构造禁止未知字段的对象 Schema。 */
    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 构造整数属性 Schema。 */
    private static Map<String, Object> integer(int minimum, int maximum, String description) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum,
                "description", description);
    }

    /** 构造字符串属性 Schema。 */
    private static Map<String, Object> string(Integer minimum, int maximum, String pattern,
                                               String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        if (minimum != null) schema.put("minLength", minimum);
        schema.put("maxLength", maximum);
        if (pattern != null) schema.put("pattern", pattern);
        schema.put("description", description);
        return schema;
    }
}
