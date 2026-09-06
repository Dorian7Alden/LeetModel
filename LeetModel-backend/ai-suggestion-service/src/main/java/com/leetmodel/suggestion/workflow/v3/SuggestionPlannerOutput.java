package com.leetmodel.suggestion.workflow.v3;

import java.io.Serializable;
import java.util.List;

/** 建议规划算子输出任务模型。 */
public record SuggestionPlannerOutput(
        List<PlannerTask> tasks
) implements Serializable {

    public record PlannerTask(
            String taskId,
            String taskType,
            String taskName,
            Integer targetQuestionNo,
            String categoryCode,
            List<String> suggestedSectionIds,
            List<String> suggestionObjectives
    ) implements Serializable {}
}
