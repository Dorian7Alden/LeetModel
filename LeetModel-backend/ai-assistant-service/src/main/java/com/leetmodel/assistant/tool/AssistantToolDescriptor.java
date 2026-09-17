package com.leetmodel.assistant.tool;

import com.leetmodel.common.ai.model.AiToolDefinition;

import java.time.Duration;
import java.util.Set;

/** 一项不可变客服工具能力描述。 */
public record AssistantToolDescriptor(
        String name,
        String toolVersion,
        AiToolDefinition definition,
        boolean terminal,
        Duration timeout,
        Set<String> allowedWorkflowVersions) {
}
