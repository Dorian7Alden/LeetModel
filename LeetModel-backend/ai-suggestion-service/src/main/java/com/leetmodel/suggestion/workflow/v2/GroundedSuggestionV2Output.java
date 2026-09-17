package com.leetmodel.suggestion.workflow.v2;

import java.util.List;

/** GROUNDED_SUGGESTION_V2 稳定报告契约。 */
public record GroundedSuggestionV2Output(
        String overallStrategy,
        List<String> topPriorities,
        List<Item> items
) {
    public record Item(String suggestionId, String priority, String category,
                       String problem, String impact, Target target,
                       List<String> actions, List<String> acceptanceCriteria,
                       List<String> paperEvidenceIds,
                       List<String> reviewFindingIds,
                       List<String> knowledgeCitationIds) {}

    public record Target(List<Integer> physicalPages, String section) {}
}
