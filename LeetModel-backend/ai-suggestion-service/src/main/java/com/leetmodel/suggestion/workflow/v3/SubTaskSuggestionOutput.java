package com.leetmodel.suggestion.workflow.v3;

import java.io.Serializable;
import java.util.List;

/** 分任务教练推演输出模型。 */
public record SubTaskSuggestionOutput(
        String taskId,
        String executionStatus,
        List<GroundedSuggestionV3Output.Item> suggestions
) implements Serializable {}
