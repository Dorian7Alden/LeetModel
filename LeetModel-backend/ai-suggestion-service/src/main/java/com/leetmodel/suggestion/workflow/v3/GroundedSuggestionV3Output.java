package com.leetmodel.suggestion.workflow.v3;

import java.io.Serializable;
import java.util.List;

/** GROUNDED_SUGGESTION_V3 结构化富文本 Markdown 建议报告契约。 */
public record GroundedSuggestionV3Output(
        String workflowVersion,
        String overallStrategy,
        List<String> topPriorities,
        List<SubTaskSummary> subTaskSummaries,
        List<Item> items
) implements Serializable {

    public record SubTaskSummary(
            String taskId,
            String taskType,
            String taskName,
            String status,
            Integer suggestionCount
    ) implements Serializable {}

    public record Item(
            String suggestionId,
            String priority,
            String type,
            String category,
            Integer subProblemNo,
            String title,
            String problemOrGap,
            String diagnosis,
            TargetLocation targetLocation,
            String actionPlanMarkdown,
            List<String> acceptanceCriteria,
            EvidenceChain evidenceChain
    ) implements Serializable {}

    public record TargetLocation(
            List<Integer> physicalPages,
            String section,
            List<String> anchorBlockIds
    ) implements Serializable {}

    public record EvidenceChain(
            List<String> paperEvidenceIds,
            List<String> reviewFindingIds,
            List<String> knowledgeCitationIds
    ) implements Serializable {}
}
