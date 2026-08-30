package com.leetmodel.assistant.tool;

/** 已完成工具集、工作流、JSON 结构和 Bean Validation 校验的调用。 */
public record PreparedAssistantToolCall(
        AssistantTool<Object> tool,
        Object input,
        String normalizedArgumentsJson) {
}
