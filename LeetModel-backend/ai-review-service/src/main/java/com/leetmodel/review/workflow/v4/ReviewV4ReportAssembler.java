package com.leetmodel.review.workflow.v4;

import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV4Output;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将 V3 科学评分事实装配为 V4 专业 Markdown 证据报告。 */
@Slf4j
@Component
public class ReviewV4ReportAssembler {

    private static final int MAX_QUOTE_LENGTH = 1200;
    private static final Pattern SCORE_IMPACT_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern EXTERNAL_MARKDOWN_IMAGE = Pattern.compile("!\\[[^]]*]\\((?:https?://|//)[^)]+\\)");
    private static final Map<String, Integer> ISSUE_PRIORITIES = Map.of(
            "P0", 0,
            "P1", 1,
            "P2", 2,
            "P3", 3
    );
    private static final Map<String, Integer> STRENGTH_IMPORTANCE = Map.of(
            "CORE", 0,
            "IMPORTANT", 1,
            "SUPPORTING", 2
    );

    private final KnowledgeRetrievalFeignClient knowledgeClient;

    public ReviewV4ReportAssembler() {
        this(null);
    }

    @Autowired
    public ReviewV4ReportAssembler(
            @Autowired(required = false) KnowledgeRetrievalFeignClient knowledgeClient
    ) {
        this.knowledgeClient = knowledgeClient;
    }

    /**
     * 构造 V4 专业报告。
     *
     * @param source V3 确定性评分结果
     * @param document 锁定的论文解析产物
     * @param problem 锁定的题目上下文
     * @return 完整 V4 输出
     */
    public DeepEvidenceReviewV4Output assemble(
            DeepEvidenceReviewV3Output source,
            PaperDocumentV2 document,
            ProblemContextDTO problem
    ) {
        Map<String, PaperDocumentV2.ContentBlockV2> blocks = indexBlocks(document);
        Map<String, String> sectionByBlock = indexSectionTitles(document);
        List<DeepEvidenceReviewV4Output.KnowledgeBasis> knowledge = retrieveKnowledge(source, problem);
        List<DeepEvidenceReviewV4Output.Finding> findings = buildFindings(
                source,
                document,
                blocks,
                sectionByBlock,
                knowledge
        );
        List<DeepEvidenceReviewV4Output.Dimension> dimensions = buildDimensions(
                source,
                findings
        );

        return DeepEvidenceReviewV4Output.builder()
                .workflowVersion(DeepEvidenceReviewV4Workflow.VERSION_CODE)
                .resultSchemaVersion(DeepEvidenceReviewV4Workflow.RESULT_SCHEMA_VERSION)
                .score(source.getScore())
                .scoreNature(source.getScoreNature())
                .overallAssessmentMarkdown(buildOverallAssessment(source, findings))
                .scoringRule(new DeepEvidenceReviewV3Output.ScoringRuleMeta(
                        DeepEvidenceReviewV4Workflow.SCORING_RULE_VERSION,
                        "数模实训第四代专业 Markdown 证据评审量表"
                ))
                .dimensions(dimensions)
                .findings(findings)
                .requirementCoverage(source.getRequirementCoverage())
                .knowledgeBasis(knowledge)
                .build();
    }

    private List<DeepEvidenceReviewV4Output.Finding> buildFindings(
            DeepEvidenceReviewV3Output source,
            PaperDocumentV2 document,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks,
            Map<String, String> sectionByBlock,
            List<DeepEvidenceReviewV4Output.KnowledgeBasis> knowledge
    ) {
        List<DeepEvidenceReviewV4Output.Finding> findings = new ArrayList<>();
        if (source.getFindings() == null) return findings;

        int evidenceSequence = 1;
        int sourceIssueCount = 0;
        Set<String> findingIds = new HashSet<>();
        Set<String> fingerprints = new HashSet<>();
        for (DeepEvidenceReviewV3Output.V3Finding item : source.getFindings()) {
            boolean strength = "STRENGTH".equalsIgnoreCase(item.getType());
            if (!strength) sourceIssueCount++;
            String category = category(item);
            String priority = strength ? null : priority(item);
            String importance = strength ? importance(item) : null;
            List<String> blockIds = validBlockIds(item.getBlockId(), item.getPhysicalPage(), blocks, document);
            if (blockIds.isEmpty()) continue;

            String rawFindingId = nullSafe(item.getFindingId()).strip();
            if (rawFindingId.isBlank()) continue;
            String findingId = rawFindingId;
            if (findingIds.contains(findingId)) {
                findingId = rawFindingId + "_" + evidenceSequence;
            }
            String statement = safeModelMarkdown(item.getStatement());
            String fingerprint = findingFingerprint(
                    strength ? "STRENGTH" : "ISSUE",
                    category,
                    blockIds,
                    statement
            );
            if (!fingerprints.add(fingerprint)) {
                continue;
            }
            findingIds.add(findingId);

            List<DeepEvidenceReviewV4Output.EvidenceQuote> quotes = new ArrayList<>();
            for (String blockId : blockIds) {
                PaperDocumentV2.ContentBlockV2 block = blocks.get(blockId);
                quotes.add(toEvidenceQuote(
                        "PE-" + String.format("%03d", evidenceSequence++),
                        block,
                        sectionByBlock.get(blockId)
                ));
            }
            List<String> basisIds = selectKnowledgeBasisIds(category, statement, knowledge);
            Integer physicalPage = blocks.get(blockIds.get(0)).physicalPage();

            findings.add(DeepEvidenceReviewV4Output.Finding.builder()
                    .findingId(findingId)
                    .findingType(strength ? "STRENGTH" : "ISSUE")
                    .priority(priority)
                    .importance(importance)
                    .dimensionCode(item.getDimensionCode())
                    .category(category)
                    .title(title(statement, strength))
                    .explanationMarkdown(explanation(statement, item.getScoreImpact(), strength))
                    .whyItMattersMarkdown(whyItMatters(item.getDimensionCode(), strength))
                    .scoreImpact(item.getScoreImpact())
                    .physicalPage(physicalPage)
                    .anchorBlockIds(blockIds)
                    .evidenceQuotes(List.copyOf(quotes))
                    .knowledgeBasisIds(basisIds)
                    .build());
        }

        findings.sort((left, right) -> {
            boolean leftIssue = "ISSUE".equals(left.getFindingType());
            boolean rightIssue = "ISSUE".equals(right.getFindingType());
            if (leftIssue != rightIssue) return leftIssue ? -1 : 1;
            if (leftIssue) {
                int priorityCompare = Integer.compare(
                        ISSUE_PRIORITIES.getOrDefault(left.getPriority(), 9),
                        ISSUE_PRIORITIES.getOrDefault(right.getPriority(), 9)
                );
                if (priorityCompare != 0) return priorityCompare;
                int scoreImpactCompare = Double.compare(
                        absoluteScoreImpact(right.getScoreImpact()),
                        absoluteScoreImpact(left.getScoreImpact())
                );
                if (scoreImpactCompare != 0) return scoreImpactCompare;
            } else {
                int importanceCompare = Integer.compare(
                        STRENGTH_IMPORTANCE.getOrDefault(left.getImportance(), 9),
                        STRENGTH_IMPORTANCE.getOrDefault(right.getImportance(), 9)
                );
                if (importanceCompare != 0) return importanceCompare;
            }
            int pageCompare = Comparator.nullsLast(Integer::compareTo)
                    .compare(left.getPhysicalPage(), right.getPhysicalPage());
            if (pageCompare != 0) return pageCompare;
            return nullSafe(left.getFindingId()).compareTo(nullSafe(right.getFindingId()));
        });
        boolean hasValidIssue = findings.stream()
                .anyMatch(item -> "ISSUE".equals(item.getFindingType()));
        if (sourceIssueCount > 0 && !hasValidIssue) {
            throw new IllegalArgumentException("V4 评审候选问题均缺少可验证论文证据");
        }
        return List.copyOf(findings);
    }

    private List<DeepEvidenceReviewV4Output.Dimension> buildDimensions(
            DeepEvidenceReviewV3Output source,
            List<DeepEvidenceReviewV4Output.Finding> findings
    ) {
        if (source.getDimensions() == null) return List.of();
        List<DeepEvidenceReviewV4Output.Dimension> dimensions = new ArrayList<>();
        for (DeepEvidenceReviewV3Output.V3ScoringDimension item : source.getDimensions()) {
            List<DeepEvidenceReviewV4Output.Finding> related = findings.stream()
                    .filter(finding -> item.getDimensionCode().equals(finding.getDimensionCode()))
                    .toList();
            List<String> strengthIds = related.stream()
                    .filter(finding -> "STRENGTH".equals(finding.getFindingType()))
                    .map(DeepEvidenceReviewV4Output.Finding::getFindingId)
                    .toList();
            List<String> issueIds = related.stream()
                    .filter(finding -> "ISSUE".equals(finding.getFindingType()))
                    .map(DeepEvidenceReviewV4Output.Finding::getFindingId)
                    .toList();
            dimensions.add(DeepEvidenceReviewV4Output.Dimension.builder()
                    .dimensionCode(item.getDimensionCode())
                    .dimensionName(item.getDimensionName())
                    .score(item.getScore())
                    .maxScore(item.getMaxScore())
                    .reasonMarkdown(dimensionReason(item, related))
                    .strengthFindingIds(strengthIds)
                    .issueFindingIds(issueIds)
                    .build());
        }
        return List.copyOf(dimensions);
    }

    private String buildOverallAssessment(
            DeepEvidenceReviewV3Output source,
            List<DeepEvidenceReviewV4Output.Finding> findings
    ) {
        List<DeepEvidenceReviewV4Output.Finding> issues = findings.stream()
                .filter(item -> "ISSUE".equals(item.getFindingType()))
                .limit(3)
                .toList();
        List<DeepEvidenceReviewV4Output.Finding> strengths = findings.stream()
                .filter(item -> "STRENGTH".equals(item.getFindingType()))
                .limit(3)
                .toList();

        StringBuilder markdown = new StringBuilder();
        markdown.append("## 综合评审\n\n")
                .append("本次平台训练评分为 **")
                .append(source.getScore())
                .append(" / 100**。该分数用于论文实训复盘，不代表赛事官方评分。\n\n");
        if (!strengths.isEmpty()) {
            markdown.append("### 值得保留的部分\n\n");
            strengths.forEach(item -> markdown.append("- **")
                    .append(item.getTitle())
                    .append("**：")
                    .append(stripMarkdown(item.getExplanationMarkdown()))
                    .append("\n"));
            markdown.append("\n");
        }
        if (!issues.isEmpty()) {
            markdown.append("### 当前优先问题\n\n");
            issues.forEach(item -> markdown.append("- **")
                    .append(item.getPriority())
                    .append(" · ")
                    .append(item.getTitle())
                    .append("**：")
                    .append(stripMarkdown(item.getWhyItMattersMarkdown()))
                    .append("\n"));
        }
        return markdown.toString().trim();
    }

    private String dimensionReason(
            DeepEvidenceReviewV3Output.V3ScoringDimension dimension,
            List<DeepEvidenceReviewV4Output.Finding> related
    ) {
        List<DeepEvidenceReviewV4Output.Finding> strengths = related.stream()
                .filter(item -> "STRENGTH".equals(item.getFindingType()))
                .limit(2)
                .toList();
        List<DeepEvidenceReviewV4Output.Finding> issues = related.stream()
                .filter(item -> "ISSUE".equals(item.getFindingType()))
                .limit(2)
                .toList();
        StringBuilder markdown = new StringBuilder();
        markdown.append("该维度得分为 **")
                .append(dimension.getScore())
                .append(" / ")
                .append(dimension.getMaxScore())
                .append("**。");
        if (!strengths.isEmpty()) {
            markdown.append("\n\n**主要优势**：")
                    .append(strengths.stream()
                            .map(DeepEvidenceReviewV4Output.Finding::getTitle)
                            .reduce((a, b) -> a + "；" + b)
                            .orElse(""));
        }
        if (!issues.isEmpty()) {
            markdown.append("\n\n**主要限制**：")
                    .append(issues.stream()
                            .map(DeepEvidenceReviewV4Output.Finding::getTitle)
                            .reduce((a, b) -> a + "；" + b)
                            .orElse(""));
        }
        return markdown.toString();
    }

    private List<DeepEvidenceReviewV4Output.KnowledgeBasis> retrieveKnowledge(
            DeepEvidenceReviewV3Output source,
            ProblemContextDTO problem
    ) {
        if (knowledgeClient == null) return List.of();
        try {
            StringBuilder query = new StringBuilder("数学建模论文专业评审。题目：")
                    .append(problem == null ? "未知题目" : problem.getTitle())
                    .append("。重点：");
            if (source.getFindings() != null) {
                source.getFindings().stream()
                        .filter(item -> !"STRENGTH".equalsIgnoreCase(item.getType()))
                        .limit(6)
                        .forEach(item -> query.append(item.getStatement()).append('；'));
            }
            KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
            request.setWorkflowVersion("AI_DIRECTORY_V1");
            request.setScene("PAPER_REVIEW");
            request.setQuery(limit(query.toString(), 3800));
            request.setTopK(6);
            request.setTokenBudget(3600);
            Result<KnowledgeRetrievalResultDTO> result = knowledgeClient.retrieve(request);
            if (result == null || !result.isSuccess() || result.getData() == null
                    || result.getData().getCitations() == null) {
                return List.of();
            }
            List<DeepEvidenceReviewV4Output.KnowledgeBasis> basis = new ArrayList<>();
            int sequence = 1;
            for (KnowledgeCitationDTO citation : result.getData().getCitations()) {
                basis.add(DeepEvidenceReviewV4Output.KnowledgeBasis.builder()
                        .basisId("KB-" + sequence++)
                        .citationId(citation.getCitationId())
                        .title(citation.getTitle())
                        .section(citation.getSection())
                        .sourcePath(citation.getSourcePath())
                        .contentHash(citation.getContentHash())
                        .authorityLevel(citation.getAuthorityLevel())
                        .supportMarkdown(limit(citation.getContent(), 800))
                        .applicabilityMarkdown(citation.getApplicability())
                        .build());
            }
            return List.copyOf(basis);
        } catch (Exception exception) {
            log.warn("V4 评审知识依据检索失败，按论文证据继续交付: {}", exception.getMessage());
            return List.of();
        }
    }

    private DeepEvidenceReviewV4Output.EvidenceQuote toEvidenceQuote(
            String evidenceId,
            PaperDocumentV2.ContentBlockV2 block,
            String sectionTitle
    ) {
        String markdown = quoteMarkdown(block);
        boolean truncated = markdown.length() > MAX_QUOTE_LENGTH;
        String safeMarkdown = truncated ? markdown.substring(0, MAX_QUOTE_LENGTH) + "\n\n> ...（原文节选）" : markdown;
        return DeepEvidenceReviewV4Output.EvidenceQuote.builder()
                .evidenceId(evidenceId)
                .blockId(block.blockId())
                .physicalPage(block.physicalPage())
                .sectionTitle(sectionTitle)
                .blockType(block.type().name())
                .quoteMarkdown(safeMarkdown)
                .contentHash("sha256:" + sha256(markdown))
                .truncated(truncated)
                .build();
    }

    private String quoteMarkdown(PaperDocumentV2.ContentBlockV2 block) {
        if (block.formula() != null && block.formula().latex() != null) {
            return "$$\n" + block.formula().latex() + "\n$$";
        }
        if (block.table() != null && block.table().html() != null) {
            return block.table().html();
        }
        if (block.code() != null && block.code().codeContent() != null) {
            String language = block.code().language() == null ? "" : block.code().language();
            return "```" + language + "\n" + block.code().codeContent() + "\n```";
        }
        if (block.figure() != null) {
            String caption = nullSafe(block.figure().caption());
            String description = nullSafe(block.figure().description());
            return "> **图像说明**：" + caption + "\n>\n> " + description;
        }
        String text = nullSafe(block.text());
        return text.lines().map(line -> "> " + line).reduce((a, b) -> a + "\n" + b).orElse("> ");
    }

    private List<String> selectKnowledgeBasisIds(
            String category,
            String statement,
            List<DeepEvidenceReviewV4Output.KnowledgeBasis> knowledge
    ) {
        if (knowledge.isEmpty()) return List.of();
        String haystack = (nullSafe(category) + " " + nullSafe(statement)).toLowerCase(Locale.ROOT);
        List<String> matched = knowledge.stream()
                .filter(item -> haystack.contains(nullSafe(item.getSection()).toLowerCase(Locale.ROOT))
                        || haystack.contains(nullSafe(item.getTitle()).toLowerCase(Locale.ROOT)))
                .map(DeepEvidenceReviewV4Output.KnowledgeBasis::getBasisId)
                .limit(2)
                .toList();
        return matched.isEmpty()
                ? knowledge.stream().map(DeepEvidenceReviewV4Output.KnowledgeBasis::getBasisId).limit(1).toList()
                : matched;
    }

    private Map<String, PaperDocumentV2.ContentBlockV2> indexBlocks(PaperDocumentV2 document) {
        Map<String, PaperDocumentV2.ContentBlockV2> result = new LinkedHashMap<>();
        if (document != null && document.blocks() != null) {
            document.blocks().forEach(block -> result.put(block.blockId(), block));
        }
        return result;
    }

    private Map<String, String> indexSectionTitles(PaperDocumentV2 document) {
        Map<String, String> titles = new HashMap<>();
        if (document == null || document.blocks() == null || document.sections() == null) return titles;
        Map<String, String> headings = new HashMap<>();
        document.sections().forEach(section -> headings.put(section.headingBlockId(), section.title()));
        String current = "";
        for (PaperDocumentV2.ContentBlockV2 block : document.blocks()) {
            if (headings.containsKey(block.blockId())) current = headings.get(block.blockId());
            titles.put(block.blockId(), current);
        }
        return titles;
    }

    private List<String> validBlockIds(
            String blockId,
            Integer physicalPage,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks,
            PaperDocumentV2 document
    ) {
        if (blockId != null && blocks.containsKey(blockId)) {
            return List.of(blockId);
        }
        if (physicalPage != null && document != null && document.blocks() != null) {
            return document.blocks().stream()
                    .filter(b -> Objects.equals(b.physicalPage(), physicalPage))
                    .map(PaperDocumentV2.ContentBlockV2::blockId)
                    .findFirst()
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return List.of();
    }

    private String priority(DeepEvidenceReviewV3Output.V3Finding finding) {
        String severity = nullSafe(finding.getSeverity()).toUpperCase(Locale.ROOT);
        return switch (severity) {
            case "BLOCKING", "CRITICAL" -> "P0";
            case "HIGH" -> "P1";
            case "MEDIUM" -> "P2";
            default -> "P3";
        };
    }

    private String importance(DeepEvidenceReviewV3Output.V3Finding finding) {
        String dimension = nullSafe(finding.getDimensionCode());
        if ("DIM_MATHEMATICAL_MODELING".equals(dimension)
                || "DIM_RESULT_VALIDATION".equals(dimension)) {
            return "CORE";
        }
        if ("DIM_ASSUMPTION_UNDERSTANDING".equals(dimension)) return "IMPORTANT";
        return "SUPPORTING";
    }

    private String category(DeepEvidenceReviewV3Output.V3Finding finding) {
        String dimension = nullSafe(finding.getDimensionCode());
        return switch (dimension) {
            case "DIM_MATHEMATICAL_MODELING" -> "MODEL";
            case "DIM_ALGORITHM_SOLUTION" -> "SOLUTION";
            case "DIM_RESULT_VALIDATION" -> "VALIDATION";
            case "DIM_ASSUMPTION_UNDERSTANDING" -> "ASSUMPTION";
            default -> "WRITING";
        };
    }

    private String title(String statement, boolean strength) {
        String text = nullSafe(statement).replaceAll("\\s+", " ").strip();
        int end = Math.min(text.length(), 42);
        String prefix = strength ? "亮点：" : "问题：";
        return prefix + text.substring(0, end) + (text.length() > end ? "…" : "");
    }

    private String explanation(
            String statement,
            String scoreImpact,
            boolean strength
    ) {
        String label = strength ? "**评审结论**" : "**问题说明**";
        return label + "：" + statement
                + "\n\n**评分影响**：" + nullSafe(scoreImpact);
    }

    private String whyItMatters(String dimensionCode, boolean strength) {
        if (strength) {
            return "该事实为当前结论提供了可核对的论文证据，修改时应优先保留其逻辑和表达。";
        }
        return switch (nullSafe(dimensionCode)) {
            case "DIM_MATHEMATICAL_MODELING" ->
                    "这会影响数学关系是否闭合，以及读者能否复核变量、约束和推导。";
            case "DIM_ALGORITHM_SOLUTION" ->
                    "这会影响求解过程的可复现性，以及数值结果是否确实由所述算法得到。";
            case "DIM_RESULT_VALIDATION" ->
                    "这会影响结果是否可信、是否稳定，以及结论能否推广到题目要求的场景。";
            case "DIM_ASSUMPTION_UNDERSTANDING" ->
                    "这会影响模型前提是否成立，以及结论在什么条件下仍然适用。";
            default -> "这会影响评阅者快速理解论文结构、证据与结论之间的关系。";
        };
    }

    private String stripMarkdown(String value) {
        return nullSafe(value).replaceAll("[#*_>`]", "").replaceAll("\\s+", " ").strip();
    }

    private String safeModelMarkdown(String value) {
        String markdown = nullSafe(value);
        return EXTERNAL_MARKDOWN_IMAGE.matcher(markdown)
                .replaceAll("[外部图片引用已省略]")
                .strip();
    }

    private String findingFingerprint(
            String findingType,
            String category,
            List<String> blockIds,
            String statement
    ) {
        String normalizedStatement = stripMarkdown(statement)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return findingType + "\0" + category + "\0"
                + String.join(",", blockIds) + "\0" + normalizedStatement;
    }

    private double absoluteScoreImpact(String value) {
        Matcher matcher = SCORE_IMPACT_NUMBER.matcher(nullSafe(value));
        if (!matcher.find()) return 0.0;
        try {
            return Math.abs(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算证据摘要", exception);
        }
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
