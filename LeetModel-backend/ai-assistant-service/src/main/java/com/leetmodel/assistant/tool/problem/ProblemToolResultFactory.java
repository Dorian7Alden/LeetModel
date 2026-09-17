package com.leetmodel.assistant.tool.problem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把 problem-service 响应收敛为给模型和审计使用的字段白名单。 */
final class ProblemToolResultFactory {

    private static final int MAX_OVERVIEW_CODE_POINTS = 500;

    private final ObjectMapper objectMapper;

    ProblemToolResultFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 创建包含可选题面概览的模型结果和不含题面正文的审计快照。 */
    AssistantToolOutput create(AssistantProblemResultDTO result) {
        try {
            // 两个视图都只复制明确允许的题目事实字段
            Map<String, Object> modelView = view(result, true);
            Map<String, Object> auditView = view(result, false);
            return AssistantToolOutput.data(objectMapper.writeValueAsString(modelView),
                    objectMapper.writeValueAsString(auditView));
        } catch (JsonProcessingException exception) {
            throw new AssistantToolException("TOOL_RESULT_INVALID", "题目工具结果无法序列化", exception);
        }
    }

    /** 构造结果白名单，最多保留五项。 */
    private Map<String, Object> view(AssistantProblemResultDTO result, boolean includeOverview) {
        Map<String, Object> view = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        List<AssistantProblemResultDTO.Item> source = result.getItems() == null
                ? List.of() : result.getItems();
        source.stream().limit(5).forEach(item -> items.add(itemView(item, includeOverview)));
        view.put("items", items);
        view.put("matchType", clean(result.getMatchType(), 30));
        view.put("truncated", Boolean.TRUE.equals(result.getTruncated()));
        view.put("matchedConditions", cleanList(result.getMatchedConditions(), 10, 80));
        return view;
    }

    /** 构造单题白名单视图。 */
    private Map<String, Object> itemView(AssistantProblemResultDTO.Item item,
                                         boolean includeOverview) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("code", item.getCode());
        view.put("title", clean(item.getTitle(), 200));
        view.put("contestCode", clean(item.getContestCode(), 32));
        view.put("contestName", clean(item.getContestName(), 100));
        view.put("year", item.getYear());
        view.put("statementLanguage", clean(item.getStatementLanguage(), 10));
        view.put("difficulty", item.getDifficulty());
        view.put("durationMinutes", item.getDurationMinutes());
        view.put("tagNames", cleanList(item.getTagNames(), 20, 50));
        if (includeOverview && item.getOverview() != null) {
            view.put("overview", clean(item.getOverview(), MAX_OVERVIEW_CODE_POINTS));
        }
        return view;
    }

    /** 清理控制字符并按 Unicode 码点截断。 */
    private String clean(String value, int maxCodePoints) {
        if (value == null) return null;
        StringBuilder builder = new StringBuilder();
        value.codePoints().filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(maxCodePoints).forEach(builder::appendCodePoint);
        return builder.toString().trim();
    }

    /** 清理有界字符串列表。 */
    private List<String> cleanList(List<String> values, int maxItems, int maxCodePoints) {
        if (values == null) return List.of();
        return values.stream().limit(maxItems).map(value -> clean(value, maxCodePoints)).toList();
    }
}
