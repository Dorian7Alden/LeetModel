package com.leetmodel.suggestion.workflow.v4;

import java.io.Serializable;
import java.util.List;

/** V4 专业、克制且有依据的 Markdown 论文建议报告。 */
public record GroundedSuggestionV4Output(
        String workflowVersion,
        String resultSchemaVersion,
        String overallStrategyMarkdown,
        List<TopPriority> topPriorities,
        List<Item> items,
        List<KnowledgeBasis> knowledgeBasis
) implements Serializable {

    public record TopPriority(
            String suggestionId,
            String title,
            String guidanceType,
            String priority,
            String summaryMarkdown
    ) implements Serializable {}

    public record Item(
            String suggestionId,
            String priority,
            String guidanceType,
            String category,
            Integer subProblemNo,
            String title,
            String currentStateMarkdown,
            String rationaleMarkdown,
            String guidanceMarkdown,
            String applicabilityMarkdown,
            TargetLocation targetLocation,
            List<EvidenceQuote> evidenceQuotes,
            List<String> acceptanceCriteriaMarkdown,
            EvidenceChain evidenceChain
    ) implements Serializable {}

    public record TargetLocation(
            List<Integer> physicalPages,
            String section,
            List<String> anchorBlockIds
    ) implements Serializable {}

    public record EvidenceQuote(
            String evidenceId,
            String blockId,
            Integer physicalPage,
            String blockType,
            String quoteMarkdown,
            String contentHash,
            Boolean truncated
    ) implements Serializable {}

    public record EvidenceChain(
            List<String> paperEvidenceIds,
            List<String> reviewFindingIds,
            List<String> knowledgeBasisIds
    ) implements Serializable {}

    public record KnowledgeBasis(
            String basisId,
            String citationId,
            String title,
            String section,
            String sourcePath,
            String contentHash,
            String authorityLevel,
            String supportMarkdown,
            String applicabilityMarkdown
    ) implements Serializable {}
}
