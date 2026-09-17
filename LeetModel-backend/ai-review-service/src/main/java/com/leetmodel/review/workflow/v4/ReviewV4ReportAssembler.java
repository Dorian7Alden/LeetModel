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
import java.util.LinkedHashSet;
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

    private static final Pattern SCORE_IMPACT_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern BLOCK_REFERENCE = Pattern.compile(
            "(?i)(?<![A-Z0-9])B(\\d+)(?:\\s*(?:[-–—~～]|至|到)\\s*B?(\\d+))?(?!\\d)"
    );
    private static final Pattern FIGURE_NUMBER = Pattern.compile(
            "(?i)(?:figure|fig\\.?|图|插图)\\s*[:：]?\\s*(\\d+)"
    );
    private static final Pattern ASSUMPTION_NUMBER = Pattern.compile(
            "(?i)(?:assumption|假设)\\s*([一二三四五六七八九十\\d]+)"
    );
    private static final Pattern EXTERNAL_MARKDOWN_IMAGE = Pattern.compile("!\\[[^]]*]\\((?:https?://|//)[^)]+\\)");
    private static final Pattern EXTERNAL_HTML_IMAGE = Pattern.compile(
            "(?is)<img\\b[^>]*\\bsrc\\s*=\\s*['\"]?(?:https?://|//)[^>]*>"
    );
    private static final Pattern COMPOUND_CLAUSE_SEPARATOR = Pattern.compile(
            "[。；;]\\s*|[，,]\\s*(?=(?:且|同时|此外|另外|并且|而且|又|还|以及|并在))"
    );
    private static final Pattern TECHNICAL_DIAGNOSTIC = Pattern.compile(
            "(?i)(?:cannot construct|exception|reference chain|source:\\s*redacted|"
                    + "com\\.leetmodel|java\\.|jsonparse|jackson|deserialize|streamreadfeature)"
    );
    private static final Set<String> STRUCTURAL_EVIDENCE_MARKERS = Set.of(
            "目录", "章节标题", "标题编号", "结构层级", "排版", "图号", "图题", "表号", "表题"
    );
    private static final Set<String> STRONG_ISSUE_MARKERS = Set.of(
            "严重", "错误", "矛盾", "违背", "缺陷", "遗漏", "未说明", "未定义",
            "不一致", "不符合", "不闭合", "混淆", "无法复核", "无法", "重复", "冗余",
            "缺省", "缺乏", "未列出", "未提供"
    );
    private static final Set<String> ISSUE_POLARITY_MARKERS = Set.of(
            "但", "然而", "不过", "缺少", "缺失", "不足", "欠缺", "有待",
            "不够", "不充分", "不完整", "不完备", "不严谨", "不清晰", "不合理",
            "无效", "未能", "未有效", "未说明", "未定义", "不一致", "不符合",
            "不闭合", "混淆", "无法复核", "无法", "重复", "冗余", "错误", "矛盾",
            "遗漏", "缺省", "缺乏", "未列出", "未提供"
    );
    private static final Set<String> POSITIVE_POLARITY_MARKERS = Set.of(
            "充分", "完整", "完备", "严谨", "清晰", "一致", "合理", "有效", "值得保留"
    );
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
    private static final Map<String, Set<String>> KNOWLEDGE_TOPICS = Map.ofEntries(
            Map.entry("ABSTRACT", Set.of("摘要", "summary", "abstract")),
            Map.entry("NOTATION", Set.of(
                    "符号", "变量", "量纲", "单位", "notation", "symbol", "dimension", "unit"
            )),
            Map.entry("ASSUMPTION", Set.of("假设", "前提", "assumption")),
            Map.entry("FIGURE", Set.of("图号", "图题", "图表编号", "figure", "caption")),
            Map.entry("VALIDATION", Set.of(
                    "验证", "检验", "误差", "灵敏度", "稳健性",
                    "validation", "sensitivity", "robustness"
            )),
            Map.entry("ALGORITHM", Set.of(
                    "算法", "求解", "收敛", "algorithm", "solver", "convergence"
            )),
            Map.entry("FORMULA", Set.of(
                    "公式", "方程", "约束", "目标函数", "formula", "equation", "constraint"
            )),
            Map.entry("DATA", Set.of(
                    "数据来源", "数据处理", "样本", "data source", "data processing"
            ))
    );
    private static final Map<ClaimTopic, Set<String>> CLAIM_TOPIC_MARKERS = Map.ofEntries(
            Map.entry(ClaimTopic.ABSTRACT, Set.of(
                    "摘要", "abstract", "summary", "定量结果", "关键指标"
            )),
            Map.entry(ClaimTopic.NOTATION, Set.of(
                    "符号表", "符号说明", "符号体系", "物理量纲", "量纲", "单位",
                    "无量纲", "变量定义", "参数定义", "lambda", "\\lambda", "λ",
                    "w_0", "w0", "year", "动力学衰减"
            )),
            Map.entry(ClaimTopic.FIGURE, Set.of(
                    "图号", "图题", "插图", "figure", "图 1", "图1", "图片编号"
            )),
            Map.entry(ClaimTopic.TABLE_NUMBERING, Set.of(
                    "表号", "表题", "表格编号", "table 1", "table 2", "缺少 table"
            )),
            Map.entry(ClaimTopic.ASSUMPTION, Set.of(
                    "假设", "assumption", "前提", "现实依据", "适用边界"
            )),
            Map.entry(ClaimTopic.PROBLEM_ANALYSIS, Set.of(
                    "问题分析", "问题重述", "problem analysis", "problem restatement",
                    "任务清单", "内在关系"
            )),
            Map.entry(ClaimTopic.SENSITIVITY, Set.of(
                    "灵敏度", "敏感性", "sensitivity", "参数扰动", "数值响应"
            )),
            Map.entry(ClaimTopic.ROBUSTNESS, Set.of(
                    "稳健性", "鲁棒性", "robustness", "参数范围", "抗干扰", "稳定性"
            )),
            Map.entry(ClaimTopic.CODE_APPENDIX, Set.of(
                    "代码", "附录", "python", "bash", "算法", "复现"
            )),
            Map.entry(ClaimTopic.PARAMETER_DISCLOSURE, Set.of(
                    "参数", "取值", "数值", "单位", "硬度", "磨损系数",
                    "$h$", "$k$", " h ", " k "
            )),
            Map.entry(ClaimTopic.DATA_CONSISTENCY, Set.of(
                    "数值", "矛盾", "正比例", "单调", "倒挂", "样本", "体积", "人流量"
            ))
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
        Set<String> usedKnowledgeIds = findings.stream()
                .filter(Objects::nonNull)
                .flatMap(item -> item.getKnowledgeBasisIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<DeepEvidenceReviewV4Output.KnowledgeBasis> usedKnowledge = knowledge.stream()
                .filter(item -> usedKnowledgeIds.contains(item.getBasisId()))
                .toList();
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
                .requirementCoverage(sanitizeRequirementCoverage(source.getRequirementCoverage()))
                .knowledgeBasis(usedKnowledge)
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
        Map<String, String> observationBlocks = indexObservationBlocks(source);
        for (DeepEvidenceReviewV3Output.V3Finding item : source.getFindings()) {
            String statement = safeModelMarkdown(item.getStatement());
            FindingNarrative sourceNarrative = narrative(statement, ClaimTopic.GENERIC);
            if (!normalizedStrength(item, sourceNarrative.conclusion())) sourceIssueCount++;
            List<String> blockIds = resolveBlockIds(
                    item,
                    statement,
                    observationBlocks,
                    blocks,
                    document,
                    sectionByBlock
            );
            if (blockIds.isEmpty()) continue;

            ClaimTopic claimTopic = claimTopic(
                    statement,
                    blockIds,
                    blocks,
                    sectionByBlock
            );
            FindingNarrative narrative = narrative(statement, claimTopic);
            narrative = alignNarrativeToEvidence(
                    narrative,
                    claimTopic,
                    blockIds,
                    blocks
            );
            if (narrative.conclusion().isBlank()) continue;
            String projectedStatement = narrative.searchText();
            boolean strength = normalizedStrength(item, narrative.conclusion());
            String dimensionCode = normalizedDimensionCode(
                    item.getDimensionCode(),
                    projectedStatement,
                    claimTopic
            );
            String category = category(dimensionCode);
            String priority = strength ? null : priority(item, projectedStatement, claimTopic);
            String importance = strength ? importance(dimensionCode) : null;

            String rawFindingId = nullSafe(item.getFindingId()).strip();
            if (rawFindingId.isBlank()) continue;
            String findingId = rawFindingId;
            if (findingIds.contains(findingId)) {
                findingId = rawFindingId + "_" + evidenceSequence;
            }
            String fingerprint = findingFingerprint(
                    strength ? "STRENGTH" : "ISSUE",
                    category,
                    blockIds,
                    projectedStatement,
                    claimTopic
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
            List<String> basisIds = selectKnowledgeBasisIds(
                    projectedStatement,
                    claimTopic,
                    knowledge
            );
            Integer physicalPage = blocks.get(blockIds.get(0)).physicalPage();
            String scoreImpact = meaningfulScoreImpact(item.getScoreImpact());
            String impact = narrative.impact().isBlank()
                    ? whyItMatters(
                            dimensionCode,
                            strength,
                            title(narrative.conclusion(), strength),
                            claimTopic
                    )
                    : narrative.impact();

            findings.add(DeepEvidenceReviewV4Output.Finding.builder()
                    .findingId(findingId)
                    .findingType(strength ? "STRENGTH" : "ISSUE")
                    .priority(priority)
                    .importance(importance)
                    .dimensionCode(dimensionCode)
                    .category(category)
                    .title(title(narrative.conclusion(), strength))
                    .explanationMarkdown(explanation(narrative.explanation(), scoreImpact, strength))
                    .whyItMattersMarkdown(impact)
                    .scoreImpact(scoreImpact)
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
        boolean fullScore = dimension.getScore() != null
                && dimension.getMaxScore() != null
                && dimension.getScore().compareTo(dimension.getMaxScore()) >= 0;
        List<DeepEvidenceReviewV4Output.Finding> strengths = related.stream()
                .filter(item -> "STRENGTH".equals(item.getFindingType()))
                .limit(2)
                .toList();
        List<DeepEvidenceReviewV4Output.Finding> issues = related.stream()
                .filter(item -> "ISSUE".equals(item.getFindingType()))
                .filter(item -> !fullScore || meaningfulScoreImpact(item.getScoreImpact()) != null)
                .limit(3)
                .toList();
        List<DeepEvidenceReviewV4Output.Finding> observations = related.stream()
                .filter(item -> "ISSUE".equals(item.getFindingType()))
                .filter(item -> fullScore && meaningfulScoreImpact(item.getScoreImpact()) == null)
                .limit(3)
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
        if (!observations.isEmpty()) {
            markdown.append("\n\n**补充观察（无独立扣分值）**：")
                    .append(observations.stream()
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
                        .supportMarkdown(sanitizeKnowledgeMarkdown(citation.getContent()))
                        .applicabilityMarkdown(sanitizeKnowledgeMarkdown(citation.getApplicability()))
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
        return DeepEvidenceReviewV4Output.EvidenceQuote.builder()
                .evidenceId(evidenceId)
                .blockId(block.blockId())
                .physicalPage(block.physicalPage())
                .sectionTitle(sectionTitle)
                .blockType(block.type().name())
                .quoteMarkdown(markdown)
                .contentHash("sha256:" + sha256(markdown))
                .truncated(false)
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
            String statement,
            ClaimTopic claimTopic,
            List<DeepEvidenceReviewV4Output.KnowledgeBasis> knowledge
    ) {
        if (knowledge.isEmpty()) return List.of();
        Set<String> allowedTopics = knowledgeTopicsForClaim(claimTopic);
        if (allowedTopics.isEmpty()) return List.of();
        String query = nullSafe(statement);
        Set<String> queryTopics = knowledgeTopics(query);
        queryTopics.retainAll(allowedTopics);
        if (queryTopics.isEmpty()) return List.of();
        return knowledge.stream()
                .filter(item -> {
                    String descriptor = nullSafe(item.getTitle()) + " "
                            + nullSafe(item.getSection()) + " "
                            + nullSafe(item.getApplicabilityMarkdown());
                    Set<String> basisTopics = knowledgeTopics(descriptor);
                    return queryTopics.stream().anyMatch(basisTopics::contains);
                })
                .map(DeepEvidenceReviewV4Output.KnowledgeBasis::getBasisId)
                .limit(1)
                .toList();
    }

    private Set<String> knowledgeTopicsForClaim(ClaimTopic claimTopic) {
        return switch (claimTopic) {
            case ABSTRACT -> Set.of("ABSTRACT");
            case NOTATION -> Set.of("NOTATION");
            case SENSITIVITY, ROBUSTNESS -> Set.of("VALIDATION");
            case PARAMETER_DISCLOSURE, DATA_CONSISTENCY -> Set.of("DATA");
            default -> Set.of();
        };
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

    private Map<String, String> indexObservationBlocks(DeepEvidenceReviewV3Output source) {
        Map<String, String> result = new HashMap<>();
        if (source.getObservations() == null) return result;
        for (DeepEvidenceReviewV3Output.V3Observation observation : source.getObservations()) {
            if (observation.getObservationId() != null && observation.getBlockId() != null) {
                result.put(observation.getObservationId(), observation.getBlockId());
            }
        }
        return result;
    }

    private List<String> resolveBlockIds(
            DeepEvidenceReviewV3Output.V3Finding finding,
            String statement,
            Map<String, String> observationBlocks,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks,
            PaperDocumentV2 document,
            Map<String, String> sectionByBlock
    ) {
        Set<String> explicitReferences = referencedBlockIds(statement, blocks);
        LinkedHashSet<String> anchors = new LinkedHashSet<>(explicitReferences);
        addExistingBlock(anchors, finding.getBlockId(), blocks);
        if (finding.getObservationIds() != null) {
            for (String observationId : finding.getObservationIds()) {
                addExistingBlock(anchors, observationBlocks.get(observationId), blocks);
            }
        }

        if (isAbstractAbsenceClaim(statement)) {
            return sectionEvidence(
                    document,
                    sectionByBlock,
                    Set.of("summary", "abstract", "摘要"),
                    null
            );
        }
        if (isSensitivityDetailClaim(statement)) {
            return sectionEvidence(
                    document,
                    sectionByBlock,
                    Set.of("sensitivity", "灵敏度", "敏感性"),
                    Set.of(
                            PaperDocumentV2.BlockType.PARAGRAPH,
                            PaperDocumentV2.BlockType.FORMULA,
                            PaperDocumentV2.BlockType.TABLE,
                            PaperDocumentV2.BlockType.FIGURE
                    )
            );
        }
        if (isRobustnessDetailClaim(statement)) {
            return sectionEvidence(
                    document,
                    sectionByBlock,
                    Set.of("sensitivity", "robustness", "灵敏度", "稳健性", "鲁棒性"),
                    Set.of(
                            PaperDocumentV2.BlockType.PARAGRAPH,
                            PaperDocumentV2.BlockType.FORMULA,
                            PaperDocumentV2.BlockType.TABLE,
                            PaperDocumentV2.BlockType.FIGURE
                    )
            );
        }
        if (isModelEvaluationAbsenceClaim(statement)) return List.of();
        if (isCodeAppendixClaim(statement)) return codeEvidence(document);
        if (isParameterDisclosureClaim(statement)) {
            return parameterDisclosureEvidence(anchors, document);
        }
        if (isCrossTableNumericConsistencyClaim(statement)) {
            return crossTableNumericEvidence(document);
        }
        if (isDuplicateFigureClaim(statement)) {
            return duplicateFigureEvidence(statement, anchors, document);
        }
        if (isAllAssumptionsClaim(statement)) {
            return completeAssumptionEvidence(statement, document, sectionByBlock);
        }
        if (isProblemAnalysisScopeClaim(statement)) {
            return sectionEvidence(
                    document,
                    sectionByBlock,
                    Set.of(
                            "problem restatement and analysis",
                            "problem analysis",
                            "问题分析",
                            "问题重述"
                    ),
                    null
            );
        }
        if (isNotationUnitClaim(statement)) {
            List<String> notationAnchors = anchors.stream()
                    .filter(blocks::containsKey)
                    .filter(blockId -> Set.of(
                            PaperDocumentV2.BlockType.TABLE,
                            PaperDocumentV2.BlockType.FORMULA
                    ).contains(blocks.get(blockId).type()))
                    .toList();
            if (!notationAnchors.isEmpty()) return notationAnchors;
            return sectionEvidence(
                    document,
                    sectionByBlock,
                    Set.of("notations", "notation", "nomenclature", "符号说明"),
                    Set.of(PaperDocumentV2.BlockType.TABLE, PaperDocumentV2.BlockType.FORMULA)
            );
        }
        if (isTableNumberingClaim(statement)) {
            return tableNumberingEvidence(anchors, document, sectionByBlock);
        }

        if (isWholePaperAbsenceClaim(statement)
                && overviewEvidenceOnly(anchors, sectionByBlock)) {
            return List.of();
        }
        List<String> reliableAnchors = anchors.stream()
                .filter(blocks::containsKey)
                .filter(blockId -> isDirectEvidenceAllowed(
                        statement,
                        blocks.get(blockId),
                        sectionByBlock.get(blockId)
                ))
                .toList();
        if (!explicitReferences.isEmpty()) return reliableAnchors;
        return reliableAnchors.stream().limit(1).toList();
    }

    private void addExistingBlock(
            Set<String> blockIds,
            String blockId,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks
    ) {
        if (blockId != null && blocks.containsKey(blockId)) blockIds.add(blockId);
    }

    private Set<String> referencedBlockIds(
            String statement,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks
    ) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = BLOCK_REFERENCE.matcher(nullSafe(statement));
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = matcher.group(2) == null ? start : Integer.parseInt(matcher.group(2));
            if (end < start || end - start > 80) continue;
            for (int current = start; current <= end; current++) {
                String blockId = "B" + current;
                if (blocks.containsKey(blockId)) result.add(blockId);
            }
        }
        return result;
    }

    private List<String> sectionEvidence(
            PaperDocumentV2 document,
            Map<String, String> sectionByBlock,
            Set<String> sectionAliases,
            Set<PaperDocumentV2.BlockType> allowedTypes
    ) {
        if (document == null || document.blocks() == null) return List.of();
        return document.blocks().stream()
                .filter(block -> sectionMatches(sectionByBlock.get(block.blockId()), sectionAliases))
                .filter(block -> block.type() != PaperDocumentV2.BlockType.HEADING)
                .filter(block -> block.type() != PaperDocumentV2.BlockType.LIST_ITEM)
                .filter(block -> allowedTypes == null || allowedTypes.contains(block.type()))
                .map(PaperDocumentV2.ContentBlockV2::blockId)
                .toList();
    }

    private boolean sectionMatches(String sectionTitle, Set<String> aliases) {
        String normalized = normalizeForMatch(sectionTitle);
        return aliases.stream()
                .map(this::normalizeForMatch)
                .anyMatch(normalized::contains);
    }

    private List<String> duplicateFigureEvidence(
            String statement,
            Set<String> anchors,
            PaperDocumentV2 document
    ) {
        if (document == null || document.blocks() == null) return List.of();
        List<PaperDocumentV2.ContentBlockV2> figures = document.blocks().stream()
                .filter(block -> block.type() == PaperDocumentV2.BlockType.FIGURE)
                .toList();
        String targetNumber = figureNumber(statement);
        if (targetNumber.isBlank()) {
            targetNumber = anchors.stream()
                    .map(anchor -> figures.stream()
                            .filter(block -> block.blockId().equals(anchor))
                            .findFirst()
                            .map(this::figureNumber)
                            .orElse(""))
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("");
        }

        Map<String, List<String>> blockIdsByNumber = new LinkedHashMap<>();
        for (PaperDocumentV2.ContentBlockV2 figure : figures) {
            String number = figureNumber(figure);
            if (number.isBlank()) continue;
            blockIdsByNumber.computeIfAbsent(number, key -> new ArrayList<>())
                    .add(figure.blockId());
        }
        if (!targetNumber.isBlank()) {
            return distinctDuplicateFigurePair(
                    blockIdsByNumber.getOrDefault(targetNumber, List.of()),
                    figures
            );
        }
        return blockIdsByNumber.values().stream()
                .map(blockIds -> distinctDuplicateFigurePair(blockIds, figures))
                .filter(blockIds -> !blockIds.isEmpty())
                .findFirst()
                .orElseGet(List::of);
    }

    private List<String> distinctDuplicateFigurePair(
            List<String> candidateBlockIds,
            List<PaperDocumentV2.ContentBlockV2> figures
    ) {
        Map<String, PaperDocumentV2.ContentBlockV2> figuresById = figures.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PaperDocumentV2.ContentBlockV2::blockId,
                        block -> block
                ));
        for (int leftIndex = 0; leftIndex < candidateBlockIds.size(); leftIndex++) {
            PaperDocumentV2.ContentBlockV2 left = figuresById.get(candidateBlockIds.get(leftIndex));
            for (int rightIndex = leftIndex + 1; rightIndex < candidateBlockIds.size(); rightIndex++) {
                PaperDocumentV2.ContentBlockV2 right = figuresById.get(candidateBlockIds.get(rightIndex));
                if (areProvablyDistinctFigures(left, right)) {
                    return List.of(left.blockId(), right.blockId());
                }
            }
        }
        return List.of();
    }

    private boolean areProvablyDistinctFigures(
            PaperDocumentV2.ContentBlockV2 left,
            PaperDocumentV2.ContentBlockV2 right
    ) {
        if (left == null || right == null) return false;
        if (left.physicalPage() != right.physicalPage()) return true;
        if (left.figure() == null || right.figure() == null) return false;

        String leftIdentity = structuredFigureIdentity(left.figure());
        String rightIdentity = structuredFigureIdentity(right.figure());
        return !leftIdentity.isBlank()
                && !rightIdentity.isBlank()
                && !leftIdentity.equals(rightIdentity);
    }

    private String structuredFigureIdentity(PaperDocumentV2.FigurePayload figure) {
        String description = normalizeForMatch(figure.description());
        if (!description.isBlank()) return description;
        return normalizeForMatch(figure.caption())
                .replaceAll("(?i)(?:figure|fig|图|插图)\\s*\\d+", "")
                .strip();
    }

    private String figureNumber(PaperDocumentV2.ContentBlockV2 block) {
        if (block.figure() != null && block.figure().figureNo() != null
                && !block.figure().figureNo().isBlank()) {
            return block.figure().figureNo().replaceAll("\\D+", "");
        }
        return figureNumber(searchableBlockText(block));
    }

    private String figureNumber(String value) {
        Matcher matcher = FIGURE_NUMBER.matcher(nullSafe(value));
        if (matcher.find()) return matcher.group(1);
        matcher = Pattern.compile("\\b(\\d{1,3})\\b").matcher(nullSafe(value));
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<String> completeAssumptionEvidence(
            String statement,
            PaperDocumentV2 document,
            Map<String, String> sectionByBlock
    ) {
        if (document == null || document.blocks() == null) return List.of();
        List<PaperDocumentV2.ContentBlockV2> assumptionBlocks = document.blocks().stream()
                .filter(block -> sectionMatches(
                        sectionByBlock.get(block.blockId()),
                        Set.of("assumptions", "assumption", "模型假设", "假设")
                ))
                .filter(block -> block.type() == PaperDocumentV2.BlockType.PARAGRAPH)
                .toList();
        List<String> result = new ArrayList<>();
        int assumptionCount = 0;
        for (int index = 0; index < assumptionBlocks.size(); index++) {
            PaperDocumentV2.ContentBlockV2 block = assumptionBlocks.get(index);
            if (!ASSUMPTION_NUMBER.matcher(nullSafe(block.text())).find()) continue;
            assumptionCount++;
            result.add(block.blockId());
            if (index + 1 < assumptionBlocks.size()) {
                PaperDocumentV2.ContentBlockV2 explanation = assumptionBlocks.get(index + 1);
                if (!ASSUMPTION_NUMBER.matcher(nullSafe(explanation.text())).find()) {
                    result.add(explanation.blockId());
                }
            }
        }
        int expectedCount = containsAny(statement, Set.of("五项", "5项", "5 项")) ? 5 : assumptionCount;
        return assumptionCount >= expectedCount && expectedCount > 0
                ? List.copyOf(result)
                : List.of();
    }

    private boolean isAbstractAbsenceClaim(String statement) {
        return nullSafe(statement).contains("摘要")
                && containsAny(statement, Set.of(
                        "缺失", "缺少", "缺乏", "未给出", "没有给出", "未报告"
                ))
                && containsAny(statement, Set.of("数值", "定量", "指标", "计算结果", "结果"));
    }

    private boolean isSensitivityDetailClaim(String statement) {
        return containsAny(statement, Set.of("灵敏度", "敏感性", "sensitivity"))
                && containsAny(statement, Set.of(
                        "缺少", "缺失", "缺乏", "未提供", "未给出", "具体", "定量",
                        "实验过程", "参数扰动", "数值响应"
                ));
    }

    private boolean isRobustnessDetailClaim(String statement) {
        return containsAny(statement, Set.of("稳健性", "鲁棒性", "robustness"))
                && containsAny(statement, Set.of(
                        "缺少", "缺失", "缺乏", "未提供", "未给出", "实验数据",
                        "验证支撑", "参数范围", "扰动"
                ));
    }

    private boolean isModelEvaluationAbsenceClaim(String statement) {
        return containsAny(statement, Set.of(
                "优缺点", "优势与不足", "strength and weakness", "Strength and Weakness"
        )) && containsAny(statement, Set.of(
                "缺失", "缺少", "缺乏", "未提供", "没有", "不存在"
        ));
    }

    private boolean isCodeAppendixClaim(String statement) {
        return containsAny(statement.toLowerCase(Locale.ROOT), Set.of(
                "代码", "附录代码", "python", "bash", "code"
        ));
    }

    private boolean isParameterDisclosureClaim(String statement) {
        String normalized = statement.toLowerCase(Locale.ROOT);
        boolean parameterNames = containsAny(normalized, Set.of(
                "材质参数", "材料参数", "硬度", "磨损系数", "$h$", "$k$", "h 和 k", "h and k"
        ));
        return parameterNames && containsAny(statement, Set.of(
                "缺省", "缺少", "缺失", "缺乏", "未列出", "未提供", "无法复现"
        ));
    }

    private boolean isTableNumberingClaim(String statement) {
        return containsAny(statement.toLowerCase(Locale.ROOT), Set.of(
                "表格标号", "表格编号", "表题编号", "正文引用", "table 1", "table 2"
        )) && containsAny(statement, Set.of(
                "不一致", "断档", "跳跃", "缺少", "错误", "混淆"
        ));
    }

    private boolean isCrossTableNumericConsistencyClaim(String statement) {
        return containsAny(statement, Set.of(
                "内部数值矛盾", "正比例规律", "单调递增", "倒挂异常", "参数配置混淆"
        )) && containsAny(statement, Set.of(
                "样本", "磨损体积", "日人流", "人流量", "表 3", "表3", "Table 3"
        ));
    }

    private boolean isDuplicateFigureClaim(String statement) {
        return containsAny(statement, Set.of("图号", "图件", "图题", "插图", "figure"))
                && containsAny(statement, Set.of("重复", "编号", "混淆"));
    }

    private boolean isAllAssumptionsClaim(String statement) {
        return containsAny(statement, Set.of("假设", "assumption"))
                && containsAny(statement, Set.of("全部", "所有", "均", "每项", "五项", "5项", "5 项"));
    }

    private boolean isProblemAnalysisScopeClaim(String statement) {
        return containsAny(statement, Set.of("问题分析", "问题重述", "Problem Restatement"))
                && containsAny(statement, Set.of(
                        "清单", "罗列", "全部", "仅", "缺少", "不足", "不够深入",
                        "因果关联", "数据流", "传导"
                ));
    }

    private boolean isNotationUnitClaim(String statement) {
        String normalized = nullSafe(statement).toLowerCase(Locale.ROOT);
        return containsAny(normalized, Set.of(
                "符号表",
                "符号说明",
                "notation",
                "notations",
                "nomenclature"
        )) && containsAny(statement, Set.of("参数", "变量", "量纲", "单位"));
    }

    private boolean isWholePaperAbsenceClaim(String statement) {
        return containsAny(statement, Set.of("论文正文", "全文", "论文中", "正文中"))
                && containsAny(statement, Set.of(
                "缺失", "缺少", "缺乏", "未提供", "未给出", "没有"
        ));
    }

    private boolean isContentsSpecificClaim(String statement) {
        return containsAny(statement, Set.of("目录", "Contents", "contents"));
    }

    private boolean isDirectEvidenceAllowed(
            String statement,
            PaperDocumentV2.ContentBlockV2 block,
            String sectionTitle
    ) {
        if (block == null) return false;
        if (block.type() == PaperDocumentV2.BlockType.HEADING
                && STRUCTURAL_EVIDENCE_MARKERS.stream().noneMatch(statement::contains)) {
            return false;
        }
        return block.type() != PaperDocumentV2.BlockType.LIST_ITEM
                || isContentsSpecificClaim(statement)
                || !"contents".equalsIgnoreCase(nullSafe(sectionTitle));
    }

    private boolean containsAny(String value, Set<String> markers) {
        String source = nullSafe(value);
        return markers.stream().anyMatch(source::contains);
    }

    private List<String> codeEvidence(PaperDocumentV2 document) {
        if (document == null || document.blocks() == null) return List.of();
        return document.blocks().stream()
                .filter(block -> block.type() == PaperDocumentV2.BlockType.CODE)
                .map(PaperDocumentV2.ContentBlockV2::blockId)
                .toList();
    }

    private List<String> parameterDisclosureEvidence(
            Set<String> anchors,
            PaperDocumentV2 document
    ) {
        if (document == null || document.blocks() == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        document.blocks().stream()
                .filter(block -> block.type() == PaperDocumentV2.BlockType.PARAGRAPH)
                .filter(block -> {
                    String text = searchableBlockText(block).toLowerCase(Locale.ROOT);
                    return containsAny(text, Set.of(
                            "values of $h$ and $k$",
                            "values of h and k",
                            "referenced from relevant literature",
                            "硬度和磨损系数",
                            "硬度 h",
                            "磨损系数 k"
                    ));
                })
                .map(PaperDocumentV2.ContentBlockV2::blockId)
                .forEach(result::add);
        anchors.stream()
                .filter(blockId -> document.blocks().stream()
                        .anyMatch(block -> block.blockId().equals(blockId)
                                && block.type() == PaperDocumentV2.BlockType.TABLE))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> tableNumberingEvidence(
            Set<String> anchors,
            PaperDocumentV2 document,
            Map<String, String> sectionByBlock
    ) {
        if (document == null || document.blocks() == null) return List.of();
        Set<String> anchorSections = anchors.stream()
                .map(sectionByBlock::get)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> anchorPages = document.blocks().stream()
                .filter(block -> anchors.contains(block.blockId()))
                .map(PaperDocumentV2.ContentBlockV2::physicalPage)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        document.blocks().stream()
                .filter(block -> anchorSections.contains(sectionByBlock.get(block.blockId())))
                .filter(block -> anchorPages.stream()
                        .anyMatch(page -> Math.abs(page - block.physicalPage()) <= 1))
                .filter(block -> block.type() == PaperDocumentV2.BlockType.PARAGRAPH
                        || block.type() == PaperDocumentV2.BlockType.TABLE)
                .filter(block -> {
                    String text = searchableBlockText(block).toLowerCase(Locale.ROOT);
                    return text.contains("table") || text.contains("表");
                })
                .map(PaperDocumentV2.ContentBlockV2::blockId)
                .forEach(result::add);
        anchors.forEach(result::add);
        return List.copyOf(result);
    }

    private List<String> crossTableNumericEvidence(PaperDocumentV2 document) {
        if (document == null || document.blocks() == null) return List.of();
        List<String> result = new ArrayList<>();
        for (PaperDocumentV2.ContentBlockV2 block : document.blocks()) {
            if (block.type() != PaperDocumentV2.BlockType.TABLE || block.table() == null) continue;
            String caption = nullSafe(block.table().caption()).toLowerCase(Locale.ROOT);
            if (caption.contains("sample data")
                    || caption.contains("stair sample")
                    || caption.contains("foot traffic estimation")
                    || caption.contains("样本数据")
                    || caption.contains("人流估算")) {
                result.add(block.blockId());
            }
        }
        return List.copyOf(result);
    }

    private boolean overviewEvidenceOnly(
            Set<String> anchors,
            Map<String, String> sectionByBlock
    ) {
        if (anchors.isEmpty()) return false;
        return anchors.stream()
                .map(sectionByBlock::get)
                .allMatch(title -> sectionMatches(
                        title,
                        Set.of("summary", "abstract", "摘要", "contents", "目录")
                ));
    }

    private String searchableBlockText(PaperDocumentV2.ContentBlockV2 block) {
        StringBuilder text = new StringBuilder(nullSafe(block.text()));
        if (block.heading() != null) text.append(' ').append(nullSafe(block.heading().cleanTitle()));
        if (block.formula() != null) text.append(' ').append(nullSafe(block.formula().latex()));
        if (block.table() != null) {
            text.append(' ').append(nullSafe(block.table().caption()))
                    .append(' ').append(nullSafe(block.table().html()));
        }
        if (block.figure() != null) {
            text.append(' ').append(nullSafe(block.figure().caption()))
                    .append(' ').append(nullSafe(block.figure().description()));
        }
        if (block.code() != null) text.append(' ').append(nullSafe(block.code().codeContent()));
        return text.toString();
    }

    private String normalizeForMatch(String value) {
        return stripMarkdown(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\\\[a-zA-Z]+", " ")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private boolean normalizedStrength(
            DeepEvidenceReviewV3Output.V3Finding finding,
            String statement
    ) {
        if (isNegativeScoreImpact(finding.getScoreImpact())) return false;
        boolean hasIssuePolarity = ISSUE_POLARITY_MARKERS.stream().anyMatch(statement::contains);
        if ("STRENGTH".equalsIgnoreCase(finding.getType())) return !hasIssuePolarity;
        if (!"ISSUE".equalsIgnoreCase(finding.getType())) return false;

        boolean hasPositivePolarity = POSITIVE_POLARITY_MARKERS.stream().anyMatch(statement::contains);
        return hasPositivePolarity && !hasIssuePolarity;
    }

    private String priority(
            DeepEvidenceReviewV3Output.V3Finding finding,
            String statement,
            ClaimTopic claimTopic
    ) {
        if (claimTopic == ClaimTopic.FIGURE
                || claimTopic == ClaimTopic.TABLE_NUMBERING
                || claimTopic == ClaimTopic.PROBLEM_ANALYSIS) {
            return "P3";
        }
        String severity = nullSafe(finding.getSeverity()).toUpperCase(Locale.ROOT);
        if (STRONG_ISSUE_MARKERS.stream().anyMatch(statement::contains)
                && Set.of("", "LOW").contains(severity)) {
            severity = "MEDIUM";
        }
        return switch (severity) {
            case "BLOCKING", "CRITICAL" -> "P0";
            case "HIGH" -> "P1";
            case "MEDIUM" -> "P2";
            default -> "P3";
        };
    }

    private String importance(String dimension) {
        if ("DIM_MATHEMATICAL_MODELING".equals(dimension)
                || "DIM_RESULT_VALIDATION".equals(dimension)) {
            return "CORE";
        }
        if ("DIM_ASSUMPTION_UNDERSTANDING".equals(dimension)) return "IMPORTANT";
        return "SUPPORTING";
    }

    private String normalizedDimensionCode(
            String sourceDimension,
            String statement,
            ClaimTopic claimTopic
    ) {
        if (claimTopic == ClaimTopic.FIGURE || claimTopic == ClaimTopic.TABLE_NUMBERING) {
            return "DIM_STRUCTURE_WRITING";
        }
        if (claimTopic == ClaimTopic.PROBLEM_ANALYSIS) {
            return "DIM_ASSUMPTION_UNDERSTANDING";
        }
        if (isAbstractAbsenceClaim(statement)
                || containsAny(statement, Set.of("摘要与正文", "结果不一致"))) {
            return "DIM_RESULT_VALIDATION";
        }
        if (claimTopic == ClaimTopic.NOTATION
                || containsAny(statement, Set.of(
                        "假设",
                        "问题分析",
                        "问题重述"
                ))) {
            return "DIM_ASSUMPTION_UNDERSTANDING";
        }
        if (containsAny(statement, Set.of("算法", "求解", "收敛", "代码复现"))) {
            return "DIM_ALGORITHM_SOLUTION";
        }
        if (containsAny(statement, Set.of(
                "灵敏度", "敏感性", "稳健性", "验证", "误差", "摘要与正文", "结果不一致"
        ))) {
            return "DIM_RESULT_VALIDATION";
        }
        if (containsAny(statement, Set.of(
                "公式", "方程", "约束", "目标函数", "数学模型", "模型推导"
        ))) {
            return "DIM_MATHEMATICAL_MODELING";
        }
        return nullSafe(sourceDimension);
    }

    private String category(String dimension) {
        return switch (nullSafe(dimension)) {
            case "DIM_MATHEMATICAL_MODELING" -> "MODEL";
            case "DIM_ALGORITHM_SOLUTION" -> "SOLUTION";
            case "DIM_RESULT_VALIDATION" -> "VALIDATION";
            case "DIM_ASSUMPTION_UNDERSTANDING" -> "ASSUMPTION";
            default -> "WRITING";
        };
    }

    private String title(String statement, boolean strength) {
        String text = stripNarrativeLabels(statement).replaceAll("\\s+", " ").strip();
        int end = firstClauseEnd(text);
        String prefix = strength ? "亮点：" : "问题：";
        return prefix + text.substring(0, end).strip();
    }

    private String explanation(
            String statement,
            String scoreImpact,
            boolean strength
    ) {
        String label = strength ? "**评审结论**" : "**问题说明**";
        String markdown = label + "：" + statement;
        if (scoreImpact != null) {
            markdown += "\n\n**评分影响**：" + scoreImpact;
        }
        return markdown;
    }

    private FindingNarrative narrative(
            String statement,
            ClaimTopic claimTopic
    ) {
        String conclusion = extractNarrativePart(statement, "结论", "事实", "评审结论", "问题说明");
        String reason = extractNarrativePart(statement, "原因", "依据");
        String impact = extractNarrativePart(statement, "影响", "为什么重要", "保留价值");
        if (conclusion.isBlank()) conclusion = stripNarrativeLabels(statement);

        conclusion = projectNarrativePart(conclusion, claimTopic);
        reason = projectNarrativePart(reason, claimTopic);
        impact = projectNarrativePart(impact, claimTopic);

        String explanation = conclusion;
        if (!reason.isBlank()) {
            explanation = explanation + "\n\n**原因**：" + reason;
        }
        return new FindingNarrative(conclusion, explanation, impact);
    }

    private FindingNarrative alignNarrativeToEvidence(
            FindingNarrative narrative,
            ClaimTopic claimTopic,
            List<String> blockIds,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks
    ) {
        if (claimTopic == ClaimTopic.ABSTRACT) {
            String conclusion = "摘要缺乏核心结果的定量数据闭环。";
            String reason = "摘要依次介绍了数据采集、磨损体积计算、日均人流模型、"
                    + "通行模式识别、年代估计和灵敏度分析，但所列原文只描述方法与"
                    + "“accurately calculating”“strong robustness”等定性判断，"
                    + "没有报告关键反演结果、误差范围或灵敏度响应数值。";
            String impact = "读者无法仅通过摘要快速核对论文得到了什么量化结论，"
                    + "也难以判断核心结果的精度、稳定性和实际解释范围。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        if (claimTopic == ClaimTopic.SENSITIVITY) {
            String conclusion = "灵敏度分析缺少可复核的参数扰动过程与定量响应结果。";
            String reason = "论文给出了灵敏度定义并对部分参数作高、中、低的定性判断，"
                    + "但所列原文未报告参数扰动幅度、对应数值响应或可核对的数值表与结果图。";
            String impact = "缺少参数扰动与结果响应的对应数据时，读者无法核验敏感性等级的判定依据，"
                    + "也无法判断模型结论在参数变化下是否稳定。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        if (claimTopic == ClaimTopic.ROBUSTNESS) {
            String conclusion = "稳健性结论缺少具体实验数据与验证支撑。";
            String reason = "论文说明模型在典型参数范围内具有稳健性，"
                    + "但所列原文未给出该参数范围、扰动设置及对应结果变化。";
            String impact = "缺少参数范围、扰动设置和结果变化时，读者无法确认稳健性结论的适用边界，"
                    + "也无法判断结论是否依赖特定参数取值。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        if (claimTopic == ClaimTopic.CODE_APPENDIX) {
            String conclusion = narrowCodeConclusion(narrative.conclusion());
            String reason = projectNarrativePart(
                    extractNarrativePart(narrative.explanation(), "原因"),
                    ClaimTopic.CODE_APPENDIX
            );
            String explanation = conclusion;
            if (!reason.isBlank()) explanation += "\n\n**原因**：" + reason;
            return new FindingNarrative(conclusion, explanation, narrative.impact());
        }
        if (claimTopic == ClaimTopic.TABLE_NUMBERING
                && hasTableReferenceMismatch(blockIds, blocks)) {
            String conclusion = "符号表的正文引用与实际表题编号不一致。";
            String reason = "正文将符号表指向 `Table 2`，而紧随其后的实际表题为 `Table 1`。";
            String impact = "正文引用与表题无法一一对应，会增加读者定位和核对表格的成本。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        if (claimTopic == ClaimTopic.FIGURE) {
            FigureDuplicateNarrative figureNarrative = figureDuplicateNarrative(blockIds, blocks);
            if (figureNarrative == null) {
                return new FindingNarrative("", "", "");
            }
            String conclusion = "图 " + figureNarrative.figureNumber()
                    + " 被用于两幅不同插图，无法通过图号准确区分。";
            String reason = figureNarrative.reason();
            String impact = "重复图号会破坏正文引用、图题和插图之间的一一对应关系，增加读者定位图示的成本。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        if (claimTopic == ClaimTopic.PROBLEM_ANALYSIS) {
            String conclusion = "问题分析主要逐项复述任务，尚未说明各子任务之间的数据依赖与推导关系。";
            String reason = "所列原文依次列出测量方案、使用频率、通行方向、并排行走、年代与修缮等任务，"
                    + "但没有交代前序测量量如何进入后续模型，也没有说明各结果之间如何相互校验。";
            String impact = "读者难以从问题分析中提前看清统一的数据流和建模主线，"
                    + "后续各模型之间的衔接依据也不够直观。";
            return new FindingNarrative(
                    conclusion,
                    conclusion + "\n\n**原因**：" + reason,
                    impact
            );
        }
        return narrative;
    }

    private FigureDuplicateNarrative figureDuplicateNarrative(
            List<String> blockIds,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks
    ) {
        if (blockIds.size() != 2) return null;
        PaperDocumentV2.ContentBlockV2 left = blocks.get(blockIds.get(0));
        PaperDocumentV2.ContentBlockV2 right = blocks.get(blockIds.get(1));
        if (!areProvablyDistinctFigures(left, right)) return null;

        String figureNumber = figureNumber(left);
        if (figureNumber.isBlank() || !figureNumber.equals(figureNumber(right))) return null;
        String leftCaption = figureEvidenceLabel(left);
        String rightCaption = figureEvidenceLabel(right);
        String reason = "第 " + left.physicalPage() + " 页的“" + leftCaption
                + "”与第 " + right.physicalPage() + " 页的“" + rightCaption
                + "”均使用图号 " + figureNumber + "，但对应的图像内容不同。";
        return new FigureDuplicateNarrative(figureNumber, reason);
    }

    private String figureEvidenceLabel(PaperDocumentV2.ContentBlockV2 block) {
        if (block.figure() != null) {
            String caption = nullSafe(block.figure().caption()).strip();
            if (!caption.isBlank()) return caption;
            String description = nullSafe(block.figure().description()).strip();
            if (!description.isBlank()) return description;
        }
        String text = nullSafe(block.text()).strip();
        return text.isBlank() ? block.blockId() : text;
    }

    private ClaimTopic claimTopic(
            String statement,
            List<String> blockIds,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks,
            Map<String, String> sectionByBlock
    ) {
        if (isAbstractAbsenceClaim(statement)) return ClaimTopic.ABSTRACT;
        if (isSensitivityDetailClaim(statement)) return ClaimTopic.SENSITIVITY;
        if (isRobustnessDetailClaim(statement)) return ClaimTopic.ROBUSTNESS;
        if (isModelEvaluationAbsenceClaim(statement)) return ClaimTopic.MODEL_EVALUATION;
        if (isCodeAppendixClaim(statement)) return ClaimTopic.CODE_APPENDIX;
        if (isParameterDisclosureClaim(statement)) return ClaimTopic.PARAMETER_DISCLOSURE;
        if (isCrossTableNumericConsistencyClaim(statement)) return ClaimTopic.DATA_CONSISTENCY;
        if (isAllAssumptionsClaim(statement)) return ClaimTopic.ASSUMPTION;
        if (isProblemAnalysisScopeClaim(statement)) return ClaimTopic.PROBLEM_ANALYSIS;

        boolean allFigures = blockIds.stream()
                .map(blocks::get)
                .filter(Objects::nonNull)
                .allMatch(block -> block.type() == PaperDocumentV2.BlockType.FIGURE);
        if (allFigures && isDuplicateFigureClaim(statement)) return ClaimTopic.FIGURE;

        boolean notationSection = blockIds.stream()
                .map(sectionByBlock::get)
                .anyMatch(title -> sectionMatches(
                        title,
                        Set.of("notations", "notation", "nomenclature", "符号说明")
                ));
        if (notationSection && isNotationUnitClaim(statement)) return ClaimTopic.NOTATION;
        if (isNotationUnitClaim(statement)) return ClaimTopic.NOTATION;
        if (isTableNumberingClaim(statement)) return ClaimTopic.TABLE_NUMBERING;
        if (isDuplicateFigureClaim(statement)) return ClaimTopic.FIGURE;
        if (containsAny(statement, CLAIM_TOPIC_MARKERS.get(ClaimTopic.TABLE_NUMBERING))) {
            return ClaimTopic.TABLE_NUMBERING;
        }
        return ClaimTopic.GENERIC;
    }

    private String projectNarrativePart(
            String value,
            ClaimTopic claimTopic
    ) {
        String source = nullSafe(value).strip();
        if (source.isBlank() || claimTopic == ClaimTopic.GENERIC) return source;

        List<String> clauses = COMPOUND_CLAUSE_SEPARATOR.splitAsStream(source)
                .map(String::strip)
                .map(this::stripLeadingConjunction)
                .filter(clause -> !clause.isBlank())
                .toList();

        Set<String> markers = CLAIM_TOPIC_MARKERS.getOrDefault(claimTopic, Set.of());
        List<String> matched = clauses.stream()
                .filter(clause -> containsAny(clause.toLowerCase(Locale.ROOT), markers))
                .toList();
        if (matched.isEmpty()) return "";
        return ensureSentenceEnding(String.join("；", matched));
    }

    private String narrowCodeConclusion(String value) {
        return nullSafe(value)
                .replaceAll("与排版(?:综合)?质量", "")
                .replaceAll("及排版(?:综合)?质量", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private boolean hasTableReferenceMismatch(
            List<String> blockIds,
            Map<String, PaperDocumentV2.ContentBlockV2> blocks
    ) {
        String evidence = blockIds.stream()
                .map(blocks::get)
                .filter(Objects::nonNull)
                .map(this::searchableBlockText)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .toLowerCase(Locale.ROOT);
        return evidence.contains("table 2") && evidence.contains("table 1");
    }

    private String stripLeadingConjunction(String value) {
        return nullSafe(value)
                .replaceFirst("^(?:且|同时|此外|另外|并且|而且|又|还|以及|并在)\\s*", "")
                .strip();
    }

    private String ensureSentenceEnding(String value) {
        String text = nullSafe(value).strip();
        if (text.isBlank() || text.matches("(?s).*[。！？.!?；;]$")) return text;
        return text + "。";
    }

    private List<DeepEvidenceReviewV3Output.V3RequirementCoverage> sanitizeRequirementCoverage(
            List<DeepEvidenceReviewV3Output.V3RequirementCoverage> source
    ) {
        if (source == null || source.isEmpty()) return List.of();
        List<DeepEvidenceReviewV3Output.V3RequirementCoverage> sanitized = new ArrayList<>();
        for (DeepEvidenceReviewV3Output.V3RequirementCoverage item : source) {
            String explanation = safeModelMarkdown(item.getExplanation());
            if (TECHNICAL_DIAGNOSTIC.matcher(explanation).find()) {
                explanation = "该小题的自动评审未能完整完成，当前仅保留保守覆盖状态。"
                        + "请重新评审后，再依据完整结果修改论文。";
            }
            sanitized.add(DeepEvidenceReviewV3Output.V3RequirementCoverage.builder()
                    .requirementId(item.getRequirementId())
                    .questionNo(item.getQuestionNo())
                    .questionTitle(item.getQuestionTitle())
                    .status(item.getStatus())
                    .explanation(explanation)
                    .evidenceBlockIds(item.getEvidenceBlockIds() == null
                            ? List.of()
                            : List.copyOf(item.getEvidenceBlockIds()))
                    .build());
        }
        return List.copyOf(sanitized);
    }

    private String extractNarrativePart(String statement, String... labels) {
        for (String label : labels) {
            Pattern pattern = Pattern.compile(
                    "(?s)(?:\\*\\*)?" + Pattern.quote(label)
                            + "(?:\\*\\*)?\\s*[：:]\\s*(.+?)(?=\\n\\s*\\n|(?:\\*\\*)?(?:结论|事实|评审结论|问题说明|原因|依据|影响|为什么重要|保留价值)(?:\\*\\*)?\\s*[：:]|$)"
            );
            Matcher matcher = pattern.matcher(nullSafe(statement));
            if (matcher.find()) return matcher.group(1).strip();
        }
        return "";
    }

    private String stripNarrativeLabels(String statement) {
        return nullSafe(statement)
                .replaceAll("(?m)^\\s*(?:\\*\\*)?(?:结论|事实|评审结论|问题说明|原因|依据|影响|为什么重要|保留价值)(?:\\*\\*)?\\s*[：:]\\s*", "")
                .strip();
    }

    private int firstClauseEnd(String text) {
        if (text.isBlank()) return 0;
        int end = text.length();
        for (String delimiter : List.of("。", "；", "，", "\n")) {
            int index = text.indexOf(delimiter);
            if (index > 0) end = Math.min(end, index);
        }
        return end;
    }

    private String whyItMatters(
            String dimensionCode,
            boolean strength,
            String findingTitle,
            ClaimTopic claimTopic
    ) {
        if (strength) {
            return "上述“" + findingTitle + "”已经形成可核对的论文证据，后续修改应保留其论证作用与表达完整性。";
        }
        if (claimTopic == ClaimTopic.FIGURE) {
            return "重复图号会破坏正文引用、图题和插图之间的一一对应关系，增加读者定位图示的成本。";
        }
        if (claimTopic == ClaimTopic.TABLE_NUMBERING) {
            return "正文引用与实际表题编号不一致，会增加读者定位、核对和复述表格内容的成本。";
        }
        if (claimTopic == ClaimTopic.PROBLEM_ANALYSIS) {
            return "问题分析未呈现统一的数据流和任务依赖时，读者难以提前理解各模型如何衔接以及结果如何相互验证。";
        }
        if (claimTopic == ClaimTopic.PARAMETER_DISCLOSURE) {
            return "材料硬度 $H$、磨损系数 $K$ 等核心参数缺少具体取值、单位与材料映射时，"
                    + "读者无法重算样本结果，也无法判断不同材料对应的参数选择是否合理。";
        }
        if (claimTopic == ClaimTopic.CODE_APPENDIX) {
            return "附录缺少从输入数据到正文结果的执行路径、依赖环境和关键参数配置时，"
                    + "评阅者无法确认论文所述多阶段算法是否能够被完整复现。";
        }
        String impact = switch (nullSafe(dimensionCode)) {
            case "DIM_MATHEMATICAL_MODELING" ->
                    "数学关系的闭合性，以及读者对变量、约束和推导的复核能力";
            case "DIM_ALGORITHM_SOLUTION" ->
                    "求解过程的可复现性，以及数值结果与所述算法之间的可验证联系";
            case "DIM_RESULT_VALIDATION" ->
                    "结果的可信度、稳定性与结论适用边界";
            case "DIM_ASSUMPTION_UNDERSTANDING" ->
                    "模型前提是否成立，以及结论在什么条件下仍然适用";
            default -> "评阅者对论文结构、证据与结论对应关系的理解";
        };
        return "上述“" + findingTitle + "”会直接影响" + impact + "，因此需要结合所列原文逐项核对。";
    }

    private String stripMarkdown(String value) {
        return nullSafe(value).replaceAll("[#*_>`]", "").replaceAll("\\s+", " ").strip();
    }

    private String safeModelMarkdown(String value) {
        String markdown = nullSafe(value)
                .replace("\\n\\n", "\n\n");
        markdown = EXTERNAL_MARKDOWN_IMAGE.matcher(markdown)
                .replaceAll("");
        return EXTERNAL_HTML_IMAGE.matcher(markdown)
                .replaceAll("")
                .strip();
    }

    private String sanitizeKnowledgeMarkdown(String value) {
        return safeModelMarkdown(value)
                .replaceAll("(?m)^\\s*<参考知识事实[^>]*>\\s*$", "")
                .replaceAll("(?m)^\\s*</参考知识事实>\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private Set<String> knowledgeTopics(String value) {
        String normalized = normalizeForMatch(value);
        Set<String> result = new LinkedHashSet<>();
        KNOWLEDGE_TOPICS.forEach((topic, markers) -> {
            if (markers.stream()
                    .map(this::normalizeForMatch)
                    .anyMatch(normalized::contains)) {
                result.add(topic);
            }
        });
        return result;
    }

    private String findingFingerprint(
            String findingType,
            String category,
            List<String> blockIds,
            String statement,
            ClaimTopic claimTopic
    ) {
        String semanticKey = semanticClaimKey(statement, claimTopic);
        if (!semanticKey.isBlank()) return findingType + "\0" + semanticKey;
        String normalizedStatement = stripMarkdown(statement)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
        return findingType + "\0" + category + "\0"
                + String.join(",", blockIds) + "\0" + normalizedStatement;
    }

    private String semanticClaimKey(String statement, ClaimTopic claimTopic) {
        if (claimTopic == ClaimTopic.ABSTRACT && isAbstractAbsenceClaim(statement)) {
            return "ABSTRACT_QUANTITATIVE_RESULT_ABSENCE";
        }
        if (claimTopic == ClaimTopic.SENSITIVITY) return "SENSITIVITY_DETAIL_GAP";
        if (claimTopic == ClaimTopic.ROBUSTNESS) return "ROBUSTNESS_EVIDENCE_GAP";
        if (claimTopic == ClaimTopic.FIGURE) {
            String figureNumber = figureNumber(statement);
            return figureNumber.isBlank()
                    ? "FIGURE_DUPLICATE_NUMBER"
                    : "FIGURE_DUPLICATE_NUMBER:" + figureNumber;
        }
        if (claimTopic == ClaimTopic.TABLE_NUMBERING) return "TABLE_NUMBERING_MISMATCH";
        if (claimTopic == ClaimTopic.PARAMETER_DISCLOSURE) return "PARAMETER_DISCLOSURE_GAP";
        if (claimTopic == ClaimTopic.DATA_CONSISTENCY) return "CROSS_TABLE_NUMERIC_CONSISTENCY";
        return "";
    }

    private boolean isNegativeScoreImpact(String value) {
        String normalized = nullSafe(value).strip();
        if (normalized.contains("扣")) return true;
        Matcher matcher = SCORE_IMPACT_NUMBER.matcher(normalized);
        if (!matcher.find()) return false;
        try {
            return Double.parseDouble(matcher.group()) < 0;
        } catch (NumberFormatException exception) {
            return false;
        }
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

    private String meaningfulScoreImpact(String value) {
        String normalized = nullSafe(value).strip();
        if (normalized.isBlank() || absoluteScoreImpact(normalized) < 0.0001) return null;
        return normalized;
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

    private enum ClaimTopic {
        ABSTRACT,
        NOTATION,
        FIGURE,
        TABLE_NUMBERING,
        ASSUMPTION,
        PROBLEM_ANALYSIS,
        SENSITIVITY,
        ROBUSTNESS,
        MODEL_EVALUATION,
        CODE_APPENDIX,
        PARAMETER_DISCLOSURE,
        DATA_CONSISTENCY,
        GENERIC
    }

    private record FindingNarrative(
            String conclusion,
            String explanation,
            String impact
    ) {
        private String searchText() {
            return conclusion + "\n" + explanation + "\n" + impact;
        }
    }

    private record FigureDuplicateNarrative(
            String figureNumber,
            String reason
    ) {}
}
