package com.leetmodel.suggestion.workflow.v4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.v3.GroundedSuggestionV3Output;
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
import java.util.Set;
import java.util.regex.Pattern;

/** 将 V3 候选建议确定性转换为 V4 三类方向建议。 */
@Component
public class SuggestionV4ReportAssembler {

    private static final Pattern EXTERNAL_MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^]]*]\\((?:https?://|//)[^)]+\\)"
    );
    private static final Pattern EXTERNAL_HTML_IMAGE = Pattern.compile(
            "(?is)<img\\b[^>]*\\bsrc\\s*=\\s*['\"]?(?:https?://|//)[^>]*>"
    );
    private static final Map<String, Integer> PRIORITIES = Map.of(
            "P0", 0,
            "P1", 1,
            "P2", 2,
            "P3", 3
    );
    private static final Map<String, Integer> GUIDANCE_TYPES = Map.of(
            "REQUIRED_FIX", 0,
            "COMPLETENESS_ENHANCEMENT", 1,
            "OPTIONAL_EXPLORATION", 2
    );
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "必须改用",
            "一定要采用",
            "唯一正确",
            "直接替换为",
            "照此修改即可",
            "保证获奖",
            "必然提升"
    );
    private static final Map<String, Set<String>> TOPIC_GROUPS = Map.ofEntries(
            Map.entry("ABSTRACT", Set.of("摘要", "关键数值", "主要结论", "summary", "abstract")),
            Map.entry("NOTATION", Set.of("符号", "变量", "下标", "定义", "notation", "symbol")),
            Map.entry("PARAMETER", Set.of("参数", "变量", "单位", "量纲", "parameter", "variable")),
            Map.entry("DIMENSION", Set.of("量纲", "单位", "物理量", "dimension", "unit")),
            Map.entry("FIGURE", Set.of("图题", "图号", "图表", "图片", "figure", "caption")),
            Map.entry("TABLE", Set.of("表题", "表号", "表格", "table")),
            Map.entry("ASSUMPTION", Set.of("假设", "前提", "适用边界", "assumption")),
            Map.entry("DATA", Set.of("数据来源", "数据处理", "样本", "缺失值", "data")),
            Map.entry("VALIDATION", Set.of("验证", "检验", "误差", "对照", "validation")),
            Map.entry("SENSITIVITY", Set.of("灵敏度", "敏感性", "稳健性", "扰动", "sensitivity", "robustness")),
            Map.entry("FORMULA", Set.of("公式", "方程", "约束", "目标函数", "推导", "equation", "constraint")),
            Map.entry("TIME", Set.of("时间尺度", "时间单位", "时段", "time scale"))
    );

    private final ObjectMapper objectMapper;

    public SuggestionV4ReportAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 装配 V4 报告。
     *
     * @param source V3 候选报告
     * @param parse 论文解析产物
     * @param reviewEvidence 锁定评审依据
     * @param knowledge 锁定知识快照
     * @return V4 建议报告
     */
    public GroundedSuggestionV4Output assemble(
            GroundedSuggestionV3Output source,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence,
            KnowledgeRetrievalResultDTO knowledge
    ) throws Exception {
        Map<String, JsonNode> paperBlocks = indexBlocks(parse);
        Map<String, ReviewEvidenceSnapshot.Finding> reviewFindings = indexFindings(reviewEvidence);
        List<GroundedSuggestionV4Output.KnowledgeBasis> knowledgeBasis = buildKnowledgeBasis(knowledge);
        List<GroundedSuggestionV4Output.Item> items = new ArrayList<>();

        if (source.items() != null) {
            for (GroundedSuggestionV3Output.Item candidate : source.items()) {
                GroundedSuggestionV4Output.Item item = toItem(
                        candidate,
                        paperBlocks,
                        reviewFindings,
                        knowledgeBasis
                );
                if (item != null) items.add(item);
            }
        }
        items = completeReviewIssueSlots(items, reviewEvidence, paperBlocks);
        items.sort(Comparator
                .comparingInt((GroundedSuggestionV4Output.Item item) ->
                        PRIORITIES.getOrDefault(item.priority(), 9))
                .thenComparingInt(item -> GUIDANCE_TYPES.getOrDefault(item.guidanceType(), 9))
                .thenComparing(item -> item.subProblemNo() == null ? Integer.MAX_VALUE : item.subProblemNo())
                .thenComparing(item -> item.targetLocation() == null
                        || item.targetLocation().physicalPages() == null
                        || item.targetLocation().physicalPages().isEmpty()
                        ? Integer.MAX_VALUE
                        : item.targetLocation().physicalPages().get(0))
                .thenComparing(GroundedSuggestionV4Output.Item::suggestionId));
        validateReviewIssueCoverage(items, reviewEvidence);

        List<GroundedSuggestionV4Output.Item> normalized = normalizeItems(items);
        validateReviewIssueCoverage(normalized, reviewEvidence);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("缺少符合 V4 语义边界的可验证建议");
        }

        List<GroundedSuggestionV4Output.TopPriority> priorities = normalized.stream()
                .limit(3)
                .map(item -> new GroundedSuggestionV4Output.TopPriority(
                        item.suggestionId(),
                        item.title(),
                        item.guidanceType(),
                        item.priority(),
                        item.currentStateMarkdown()
                ))
                .toList();

        Set<String> usedBasisIds = normalized.stream()
                .flatMap(item -> item.evidenceChain().knowledgeBasisIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<GroundedSuggestionV4Output.KnowledgeBasis> usedKnowledgeBasis = knowledgeBasis.stream()
                .filter(item -> usedBasisIds.contains(item.basisId()))
                .toList();

        return new GroundedSuggestionV4Output(
                GroundedSuggestionV4Workflow.VERSION,
                GroundedSuggestionV4Workflow.RESULT_SCHEMA_VERSION,
                overallStrategy(source, normalized),
                priorities,
                List.copyOf(normalized),
                usedKnowledgeBasis
        );
    }

    private GroundedSuggestionV4Output.Item toItem(
            GroundedSuggestionV3Output.Item source,
            Map<String, JsonNode> paperBlocks,
            Map<String, ReviewEvidenceSnapshot.Finding> reviewFindings,
            List<GroundedSuggestionV4Output.KnowledgeBasis> knowledgeBasis
    ) {
        if (source == null || source.evidenceChain() == null
                || source.evidenceChain().paperEvidenceIds() == null
                || source.evidenceChain().paperEvidenceIds().isEmpty()) {
            return null;
        }
        List<String> paperIds = source.evidenceChain().paperEvidenceIds().stream()
                .filter(paperBlocks::containsKey)
                .distinct()
                .toList();
        if (paperIds.isEmpty()) return null;

        List<String> reviewIds = source.evidenceChain().reviewFindingIds() == null
                ? List.of()
                : source.evidenceChain().reviewFindingIds().stream()
                .filter(reviewFindings::containsKey)
                .distinct()
                .toList();
        if (reviewIds.size() > 1) return null;

        String guidanceType = guidanceType(source, reviewIds);
        if ("REQUIRED_FIX".equals(guidanceType) && reviewIds.isEmpty()) return null;
        String priority = calibratedPriority(source.priority(), reviewIds, reviewFindings);

        ReviewEvidenceSnapshot.Finding reviewFinding = reviewIds.isEmpty()
                ? null
                : reviewFindings.get(reviewIds.get(0));
        if (reviewFinding != null) {
            if (!Set.of("ISSUE", "WEAKNESS").contains(nullSafe(reviewFinding.type()).toUpperCase(Locale.ROOT))) {
                return null;
            }
            List<String> completePaperIds = reviewFinding.paperEvidenceIds() == null
                    ? List.of()
                    : reviewFinding.paperEvidenceIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            if (completePaperIds.isEmpty() || !paperBlocks.keySet().containsAll(completePaperIds)) {
                return null;
            }
            paperIds = completePaperIds;
        }

        List<GroundedSuggestionV4Output.EvidenceQuote> quotes = new ArrayList<>();
        int quoteSequence = 1;
        for (String blockId : paperIds) {
            quotes.add(toQuote("PE-" + quoteSequence++, paperBlocks.get(blockId)));
        }
        List<String> basisIds = matchedKnowledgeBasisIds(source, reviewFinding, knowledgeBasis);

        String guidance = cleanGuidance(
                soften(nullSafe(source.actionPlanMarkdown()), guidanceType)
        );
        if (guidance.isBlank()) return null;
        if (containsForbiddenPhrase(guidance)) return null;
        if (!guidanceMatchesFinding(guidance, reviewFinding)) return null;
        boolean guardedModelingGuidance = reviewFinding != null
                && isModelIdentifiabilityFinding(reviewFinding);
        if (guardedModelingGuidance) {
            guidance = fallbackGuidance(reviewFinding);
        }
        List<String> acceptanceCriteria = source.acceptanceCriteria() == null
                || source.acceptanceCriteria().isEmpty()
                ? List.of("相关说明、证据和结论之间能够形成可复核的闭环。")
                : source.acceptanceCriteria();
        if (guardedModelingGuidance) {
            acceptanceCriteria = List.of(
                    "补充后的说明能够明确未知量、约束条件、可辨识性及其对结论适用边界的影响，"
                            + "并与对应原文依据保持一致。"
            );
        }
        if (!guardedModelingGuidance && reviewFinding != null && acceptanceCriteria.stream()
                .anyMatch(criterion -> !guidanceMatchesFinding(criterion, reviewFinding))) {
            return null;
        }

        GroundedSuggestionV3Output.TargetLocation location = source.targetLocation();
        GroundedSuggestionV4Output.TargetLocation targetLocation =
                new GroundedSuggestionV4Output.TargetLocation(
                        quotes.stream()
                                .map(GroundedSuggestionV4Output.EvidenceQuote::physicalPage)
                                .distinct()
                                .toList(),
                        location == null ? "" : location.section(),
                        paperIds
        );
        String currentState = reviewFinding == null
                ? nullSafe(source.problemOrGap())
                : reviewCurrentState(reviewFinding.statement());
        String title = reviewFinding == null
                ? nullSafe(source.title())
                : topicTitle(currentState);
        String rationale = reviewFinding == null
                || nullSafe(reviewFinding.rationaleMarkdown()).isBlank()
                ? nullSafe(source.diagnosis())
                : cleanReviewStatement(reviewFinding.rationaleMarkdown());

        return new GroundedSuggestionV4Output.Item(
                source.suggestionId() == null ? "S-0" : source.suggestionId(),
                priority,
                guidanceType,
                source.category() == null ? "MODEL" : source.category(),
                source.subProblemNo(),
                title.isBlank() ? "论文完善方向" : title,
                currentState,
                rationale,
                guidance,
                applicability(guidanceType, source.category()),
                targetLocation,
                List.copyOf(quotes),
                acceptanceCriteria,
                new GroundedSuggestionV4Output.EvidenceChain(
                        paperIds,
                        reviewIds,
                        basisIds
                )
        );
    }

    private String guidanceType(
            GroundedSuggestionV3Output.Item item,
            List<String> reviewIds
    ) {
        if (!reviewIds.isEmpty()) return "REQUIRED_FIX";
        return "CORRECTION".equals(item.type())
                ? "COMPLETENESS_ENHANCEMENT"
                : "OPTIONAL_EXPLORATION";
    }

    private String calibratedPriority(
            String candidatePriority,
            List<String> reviewIds,
            Map<String, ReviewEvidenceSnapshot.Finding> findings
    ) {
        String calibrated = normalizePriority(candidatePriority);
        int calibratedRank = PRIORITIES.get(calibrated);
        for (String reviewId : reviewIds) {
            ReviewEvidenceSnapshot.Finding finding = findings.get(reviewId);
            if (finding == null) continue;
            String reviewPriority = switch (nullSafe(finding.severity()).toUpperCase(Locale.ROOT)) {
                case "P0", "BLOCKING", "CRITICAL" -> "P0";
                case "P1", "HIGH" -> "P1";
                case "P2", "MEDIUM" -> "P2";
                default -> "P3";
            };
            int reviewRank = PRIORITIES.get(reviewPriority);
            if (reviewRank < calibratedRank) {
                calibrated = reviewPriority;
                calibratedRank = reviewRank;
            }
        }
        return calibrated;
    }

    private String soften(String markdown, String guidanceType) {
        String result = markdown
                .replace("必须改用其他模型", "补充对当前模型适用性与该确定性问题修正方式的说明；若现有模型无法消除该问题，再比较替代方案")
                .replace("必须改用", "补充对当前方法适用性与修正方向的说明，可比较")
                .replace("一定要采用", "可结合适用条件考虑")
                .replace("唯一正确的做法是", "一种可选的核验方向是")
                .replace("唯一正确", "可选方向")
                .replace("直接替换为", "可进一步比较")
                .replace("照此修改即可", "可据此检查相关说明是否完整")
                .replace("保证获奖", "增强论证完整性")
                .replace("必然提升", "有助于增强")
                .replace("建议将", "可考虑补充")
                .replace("建议采用", "可考虑")
                .replace("参数推荐", "参数说明方向")
                .replace("修改方案", "完善方向");
        if ("OPTIONAL_EXPLORATION".equals(guidanceType)
                && !result.startsWith("### 可选探索")) {
            result = "### 可选探索方向\n\n若希望进一步增强现有论证的说服力，可以从以下方向选择性补充：\n\n"
                    + result;
        }
        if ("OPTIONAL_EXPLORATION".equals(guidanceType)) {
            result = result
                    .replace("建议在", "可考虑在")
                    .replace("建议对", "可考虑补充对")
                    .replace("建议完善", "可考虑补充完善")
                    .replace("建议补充", "可考虑补充");
        }
        if ("REQUIRED_FIX".equals(guidanceType)) {
            result = result
                    .replaceFirst("^\\s*建议从(.+?)角度[^：:]*[：:]\\s*", "补充$1方面的说明：\n")
                    .replaceFirst("(?m)^\\s*#{1,6}\\s*可选探索方向\\s*\\n+", "")
                    .replaceFirst("(?m)^\\s*若希望进一步增强现有论证的说服力，"
                            + "可以从以下方向选择性补充[：:]?\\s*\\n+", "")
                    .replaceFirst("^\\s*建议(?:进一步)?(?:补充|完善)", "补充")
                    .replaceFirst("^\\s*建议对(.+?)进行[^：:]*[：:]\\s*", "补充$1相关说明：\n")
                    .strip();
        }
        return result;
    }

    private List<String> matchedKnowledgeBasisIds(
            GroundedSuggestionV3Output.Item source,
            ReviewEvidenceSnapshot.Finding reviewFinding,
            List<GroundedSuggestionV4Output.KnowledgeBasis> knowledgeBasis
    ) {
        if (source.evidenceChain().knowledgeCitationIds() == null
                || source.evidenceChain().knowledgeCitationIds().isEmpty()) {
            return List.of();
        }
        Map<String, String> basisIdByCitationId = new HashMap<>();
        for (GroundedSuggestionV4Output.KnowledgeBasis basis : knowledgeBasis) {
            if (basis.citationId() != null && !basis.citationId().isBlank()) {
                basisIdByCitationId.put(basis.citationId(), basis.basisId());
            }
        }
        String topic = nullSafe(source.title()) + " "
                + nullSafe(source.problemOrGap()) + " "
                + nullSafe(source.diagnosis()) + " "
                + nullSafe(source.actionPlanMarkdown()) + " "
                + (reviewFinding == null ? "" : nullSafe(reviewFinding.statement()));
        return source.evidenceChain().knowledgeCitationIds().stream()
                .map(basisIdByCitationId::get)
                .filter(java.util.Objects::nonNull)
                .filter(basisId -> knowledgeBasis.stream()
                        .filter(item -> item.basisId().equals(basisId))
                        .findFirst()
                        .map(item -> knowledgeMatchesTopic(item, topic))
                        .orElse(false))
                .distinct()
                .limit(2)
                .toList();
    }

    private String overallStrategy(
            GroundedSuggestionV3Output source,
            List<GroundedSuggestionV4Output.Item> items
    ) {
        long required = items.stream().filter(item -> "REQUIRED_FIX".equals(item.guidanceType())).count();
        long enhancement = items.stream()
                .filter(item -> "COMPLETENESS_ENHANCEMENT".equals(item.guidanceType())).count();
        long optional = items.stream()
                .filter(item -> "OPTIONAL_EXPLORATION".equals(item.guidanceType())).count();
        return "## 本轮论文完善主线\n\n"
                + "本报告识别出 **" + required + "** 项必要修正、**"
                + enhancement + "** 项完整性增强和 **" + optional + "** 项可选探索。\n\n"
                + "优先处理有明确论文与评审依据的确定性问题，再补充解释、验证和适用边界。"
                + "可选探索仅用于提供思考方向，不代表现有模型必须被替换。";
    }

    private List<GroundedSuggestionV4Output.KnowledgeBasis> buildKnowledgeBasis(
            KnowledgeRetrievalResultDTO knowledge
    ) {
        if (knowledge == null || knowledge.getCitations() == null) return List.of();
        List<GroundedSuggestionV4Output.KnowledgeBasis> result = new ArrayList<>();
        int sequence = 1;
        for (KnowledgeCitationDTO citation : knowledge.getCitations()) {
            result.add(new GroundedSuggestionV4Output.KnowledgeBasis(
                    "KB-" + sequence++,
                    citation.getCitationId(),
                    citation.getTitle(),
                    citation.getSection(),
                    citation.getSourcePath(),
                    citation.getContentHash(),
                    citation.getAuthorityLevel(),
                    sanitizeKnowledgeMarkdown(citation.getContent()),
                    sanitizeKnowledgeMarkdown(citation.getApplicability())
            ));
        }
        return List.copyOf(result);
    }

    private GroundedSuggestionV4Output.EvidenceQuote toQuote(String evidenceId, JsonNode block) {
        String markdown = blockMarkdown(block);
        return new GroundedSuggestionV4Output.EvidenceQuote(
                evidenceId,
                block.path("blockId").asText(),
                block.path("physicalPage").asInt(1),
                block.path("type").asText("PARAGRAPH"),
                markdown,
                "sha256:" + sha256(markdown),
                false
        );
    }

    private String blockMarkdown(JsonNode block) {
        if (block.path("formula").isObject() && !block.path("formula").path("latex").asText().isBlank()) {
            return "$$\n" + block.path("formula").path("latex").asText() + "\n$$";
        }
        if (block.path("table").isObject() && !block.path("table").path("html").asText().isBlank()) {
            return block.path("table").path("html").asText();
        }
        if (block.path("code").isObject() && !block.path("code").path("codeContent").asText().isBlank()) {
            return "```" + block.path("code").path("language").asText("") + "\n"
                    + block.path("code").path("codeContent").asText() + "\n```";
        }
        if (block.path("figure").isObject()) {
            return "> **图像说明**：" + block.path("figure").path("caption").asText("")
                    + "\n>\n> " + block.path("figure").path("description").asText("");
        }
        return block.path("text").asText("").lines()
                .map(line -> "> " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("> ");
    }

    private Map<String, JsonNode> indexBlocks(PaperParseDTO parse) throws Exception {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        if (parse == null || parse.getDocumentJson() == null) return result;
        JsonNode root = objectMapper.readTree(parse.getDocumentJson());
        for (JsonNode block : root.path("blocks")) {
            String blockId = block.path("blockId").asText();
            if (!blockId.isBlank()) result.put(blockId, block);
        }
        return result;
    }

    private Map<String, ReviewEvidenceSnapshot.Finding> indexFindings(
            ReviewEvidenceSnapshot snapshot
    ) {
        Map<String, ReviewEvidenceSnapshot.Finding> result = new HashMap<>();
        if (snapshot != null && snapshot.findings() != null) {
            snapshot.findings().forEach(item -> result.put(item.findingId(), item));
        }
        return result;
    }

    /**
     * 按锁定评审问题建立确定性建议槽位。
     *
     * @param items 已通过语义校验的模型候选
     * @param snapshot 锁定评审依据
     * @param paperBlocks 锁定论文解析块
     * @return 每个评审问题恰好保留一个条目的候选集合
     */
    private List<GroundedSuggestionV4Output.Item> completeReviewIssueSlots(
            List<GroundedSuggestionV4Output.Item> items,
            ReviewEvidenceSnapshot snapshot,
            Map<String, JsonNode> paperBlocks
    ) {
        Map<String, ReviewEvidenceSnapshot.Finding> requiredFindings = requiredFindings(snapshot);
        if (requiredFindings.isEmpty()) return items;

        Map<String, GroundedSuggestionV4Output.Item> selectedByFinding = new LinkedHashMap<>();
        List<GroundedSuggestionV4Output.Item> independentItems = new ArrayList<>();
        for (GroundedSuggestionV4Output.Item item : items) {
            List<String> reviewIds = reviewFindingIds(item);
            if (reviewIds.isEmpty()) {
                independentItems.add(item);
                continue;
            }
            String findingId = reviewIds.get(0);
            if (requiredFindings.containsKey(findingId)) {
                selectedByFinding.putIfAbsent(findingId, item);
            }
        }

        List<GroundedSuggestionV4Output.Item> completed = new ArrayList<>();
        for (ReviewEvidenceSnapshot.Finding finding : requiredFindings.values()) {
            GroundedSuggestionV4Output.Item selected = selectedByFinding.get(finding.findingId());
            if (selected == null) {
                selected = buildFindingFallback(finding, paperBlocks);
            }
            completed.add(selected);
        }
        completed.addAll(independentItems);
        return completed;
    }

    /**
     * 为模型漏掉的评审问题生成同主题、可核验的保守兜底。
     *
     * @param finding 锁定评审问题
     * @param paperBlocks 锁定论文解析块
     * @return 仅绑定当前 finding 的必要修正条目
     */
    private GroundedSuggestionV4Output.Item buildFindingFallback(
            ReviewEvidenceSnapshot.Finding finding,
            Map<String, JsonNode> paperBlocks
    ) {
        List<String> paperIds = normalizedFindingPaperIds(finding);
        if (paperIds.isEmpty() || !paperBlocks.keySet().containsAll(paperIds)) {
            throw new IllegalArgumentException(
                    "V4 评审问题缺少可用论文依据: findingId=" + finding.findingId()
            );
        }

        List<GroundedSuggestionV4Output.EvidenceQuote> quotes = new ArrayList<>();
        int quoteSequence = 1;
        for (String blockId : paperIds) {
            quotes.add(toQuote("PE-" + quoteSequence++, paperBlocks.get(blockId)));
        }

        String currentState = reviewCurrentState(finding.statement());
        String title = topicTitle(currentState);
        String category = normalizedCategory(finding.category());
        String rationale = cleanReviewStatement(finding.rationaleMarkdown());
        if (rationale.isBlank()) {
            rationale = "该问题会削弱论文内容与原文证据之间的对应关系，使相关论证难以直接复核。";
        }

        List<Integer> physicalPages = quotes.stream()
                .map(GroundedSuggestionV4Output.EvidenceQuote::physicalPage)
                .distinct()
                .toList();
        GroundedSuggestionV4Output.TargetLocation targetLocation =
                new GroundedSuggestionV4Output.TargetLocation(
                        physicalPages,
                        "",
                        paperIds
                );

        String guidance = fallbackGuidance(finding);
        String acceptanceCriterion = "补充后的内容能够直接回应“"
                + (title.isBlank() ? currentState : title)
                + "”，并可由对应原文依据复核。";
        return new GroundedSuggestionV4Output.Item(
                "FALLBACK-" + finding.findingId(),
                priorityFromSeverity(finding.severity()),
                "REQUIRED_FIX",
                category,
                null,
                title.isBlank() ? "补充问题对应说明" : title,
                currentState,
                rationale,
                guidance,
                applicability("REQUIRED_FIX", category),
                targetLocation,
                List.copyOf(quotes),
                List.of(acceptanceCriterion),
                new GroundedSuggestionV4Output.EvidenceChain(
                        paperIds,
                        List.of(finding.findingId()),
                        List.of()
                )
        );
    }

    /**
     * 生成不指定唯一建模路径的确定性补充方向。
     *
     * @param finding 锁定评审问题
     * @return 与当前问题同主题的 Markdown 建议
     */
    private String fallbackGuidance(ReviewEvidenceSnapshot.Finding finding) {
        Set<String> topics = topicGroups(
                nullSafe(finding.statement()) + " " + nullSafe(finding.rationaleMarkdown())
        );
        if (topics.contains("ABSTRACT")) {
            return "补充摘要中与该问题直接相关的方法、关键结果或结论说明，"
                    + "并核对其与正文对应内容保持一致。";
        }
        if (isDataConsistencyFinding(finding)) {
            return "补充对该不一致现象的复核说明，包括数据、样本或计算口径，"
                    + "以及其与理论公式和正文结论之间的对应关系。";
        }
        if (isModelIdentifiabilityFinding(finding)) {
            return "补充对该反演或求解关系中未知量、约束条件、可辨识性和误差传播的说明，"
                    + "并核对输入、参数、约束与输出之间是否形成闭合关系，"
                    + "同时说明当前结论在何种条件下成立。";
        }
        if (topics.contains("FIGURE")) {
            return "补充相关图号、图题或正文引用之间的对应说明，"
                    + "使每幅图的含义和引用位置能够被准确识别。";
        }
        if (topics.contains("TABLE")) {
            return "补充相关表号、表题、字段含义或正文引用之间的对应说明，"
                    + "使表格信息能够被直接复核。";
        }
        if (topics.contains("DIMENSION") || topics.contains("FORMULA")
                || topics.contains("NOTATION") || topics.contains("PARAMETER")) {
            return "补充相关符号、参数、单位、量纲或公式关系的说明，"
                    + "并核对定义、推导与正文使用方式保持一致。";
        }
        if (topics.contains("VALIDATION") || topics.contains("SENSITIVITY")) {
            return "补充与该问题直接相关的验证依据、误差解释或适用边界说明，"
                    + "使结论的可靠性能够被复核。";
        }
        if (topics.contains("DATA")) {
            return "补充与该问题直接相关的数据来源、处理过程或样本限制说明，"
                    + "使数据与后续结论之间形成清晰对应。";
        }
        if (topics.contains("ASSUMPTION")) {
            return "补充相关假设的依据、适用条件和可能影响，"
                    + "说明其与当前问题及后续结论之间的关系。";
        }
        if (topics.contains("TIME")) {
            return "补充相关时间尺度、时段划分或时间单位的说明，"
                    + "并核对其与模型输入和结果解释保持一致。";
        }
        return "补充与上述问题直接对应的说明或修正依据，"
                + "使原文表述、相关证据与结论能够相互对应。";
    }

    private boolean isDataConsistencyFinding(ReviewEvidenceSnapshot.Finding finding) {
        String text = nullSafe(finding.statement()) + " " + nullSafe(finding.rationaleMarkdown());
        return Set.of(
                        "不一致",
                        "矛盾",
                        "相反",
                        "反向",
                        "冲突",
                        "背离",
                        "无法对应",
                        "未能对应"
                ).stream()
                .anyMatch(text::contains);
    }

    private boolean isModelIdentifiabilityFinding(ReviewEvidenceSnapshot.Finding finding) {
        String text = nullSafe(finding.statement()) + " " + nullSafe(finding.rationaleMarkdown());
        return Set.of(
                        "欠定",
                        "循环",
                        "无法唯一确定",
                        "不可辨识",
                        "不适定",
                        "病态",
                        "反演",
                        "underdetermined",
                        "identifiability",
                        "ill-posed"
                ).stream()
                .anyMatch(marker -> text.toLowerCase(Locale.ROOT).contains(marker));
    }

    /**
     * 去重、限制非必要建议数量并重新生成连续 ID。
     *
     * @param items 已补齐评审问题槽位并完成排序的建议
     * @return 最终规范化建议
     */
    private List<GroundedSuggestionV4Output.Item> normalizeItems(
            List<GroundedSuggestionV4Output.Item> items
    ) {
        List<GroundedSuggestionV4Output.Item> deduplicated = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (GroundedSuggestionV4Output.Item item : items) {
            String fingerprint = suggestionFingerprint(item);
            if (fingerprints.add(fingerprint)) deduplicated.add(item);
        }

        long requiredCount = deduplicated.stream()
                .filter(this::isReviewRequiredItem)
                .count();
        if (requiredCount > 10) {
            throw new IllegalArgumentException("V4 必要修正数量超过报告上限: " + requiredCount);
        }

        List<GroundedSuggestionV4Output.Item> limited = new ArrayList<>();
        for (GroundedSuggestionV4Output.Item item : deduplicated) {
            if (limited.size() < 10 || isReviewRequiredItem(item)) {
                limited.add(item);
            }
        }
        while (limited.size() > 10) {
            int removableIndex = lastIndependentItemIndex(limited);
            if (removableIndex < 0) break;
            limited.remove(removableIndex);
        }

        List<GroundedSuggestionV4Output.Item> normalized = new ArrayList<>();
        int sequence = 1;
        for (GroundedSuggestionV4Output.Item item : limited) {
            normalized.add(withId(item, "S-" + sequence++));
        }
        return normalized;
    }

    private int lastIndependentItemIndex(List<GroundedSuggestionV4Output.Item> items) {
        for (int index = items.size() - 1; index >= 0; index--) {
            if (!isReviewRequiredItem(items.get(index))) return index;
        }
        return -1;
    }

    private boolean isReviewRequiredItem(GroundedSuggestionV4Output.Item item) {
        return "REQUIRED_FIX".equals(item.guidanceType())
                && !reviewFindingIds(item).isEmpty();
    }

    private List<String> reviewFindingIds(GroundedSuggestionV4Output.Item item) {
        if (item == null || item.evidenceChain() == null
                || item.evidenceChain().reviewFindingIds() == null) {
            return List.of();
        }
        return item.evidenceChain().reviewFindingIds();
    }

    private Map<String, ReviewEvidenceSnapshot.Finding> requiredFindings(
            ReviewEvidenceSnapshot snapshot
    ) {
        Map<String, ReviewEvidenceSnapshot.Finding> result = new LinkedHashMap<>();
        if (snapshot == null || snapshot.findings() == null) return result;
        for (ReviewEvidenceSnapshot.Finding finding : snapshot.findings()) {
            String type = nullSafe(finding.type()).toUpperCase(Locale.ROOT);
            if (Set.of("ISSUE", "WEAKNESS").contains(type)) {
                result.put(finding.findingId(), finding);
            }
        }
        return result;
    }

    private List<String> normalizedFindingPaperIds(ReviewEvidenceSnapshot.Finding finding) {
        if (finding == null || finding.paperEvidenceIds() == null) return List.of();
        return finding.paperEvidenceIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private String normalizedCategory(String category) {
        return nullSafe(category).isBlank() ? "MODEL" : category;
    }

    private String priorityFromSeverity(String severity) {
        return switch (nullSafe(severity).toUpperCase(Locale.ROOT)) {
            case "P0", "BLOCKING", "CRITICAL" -> "P0";
            case "P1", "HIGH" -> "P1";
            case "P2", "MEDIUM" -> "P2";
            default -> "P3";
        };
    }

    /**
     * 校验锁定评审中的全部问题与最终建议保持一一对应。
     *
     * @param items 待发布的 V4 建议
     * @param snapshot 锁定评审依据
     */
    private void validateReviewIssueCoverage(
            List<GroundedSuggestionV4Output.Item> items,
            ReviewEvidenceSnapshot snapshot
    ) {
        Map<String, ReviewEvidenceSnapshot.Finding> requiredFindings = requiredFindings(snapshot);
        if (requiredFindings.isEmpty()) return;

        Map<String, Integer> referenceCounts = new LinkedHashMap<>();
        List<String> evidenceMismatches = new ArrayList<>();
        for (GroundedSuggestionV4Output.Item item : items) {
            if (item.evidenceChain() == null
                    || item.evidenceChain().reviewFindingIds() == null) {
                continue;
            }
            for (String findingId : item.evidenceChain().reviewFindingIds()) {
                ReviewEvidenceSnapshot.Finding finding = requiredFindings.get(findingId);
                if (finding == null) continue;
                referenceCounts.merge(findingId, 1, Integer::sum);
                List<String> expectedPaperIds = normalizedFindingPaperIds(finding);
                List<String> actualPaperIds = item.evidenceChain().paperEvidenceIds() == null
                        ? List.of()
                        : item.evidenceChain().paperEvidenceIds();
                if (!actualPaperIds.equals(expectedPaperIds)) {
                    evidenceMismatches.add(findingId);
                }
            }
        }

        List<String> missing = requiredFindings.keySet().stream()
                .filter(id -> referenceCounts.getOrDefault(id, 0) == 0)
                .toList();
        List<String> duplicate = requiredFindings.keySet().stream()
                .filter(id -> referenceCounts.getOrDefault(id, 0) > 1)
                .toList();
        if (missing.isEmpty() && duplicate.isEmpty() && evidenceMismatches.isEmpty()) return;

        throw new IllegalArgumentException(
                "V4 评审问题覆盖校验失败: missing=" + missing
                        + ", duplicate=" + duplicate
                        + ", evidenceMismatch=" + evidenceMismatches.stream().distinct().toList()
        );
    }

    private GroundedSuggestionV4Output.Item withId(
            GroundedSuggestionV4Output.Item item,
            String id
    ) {
        return new GroundedSuggestionV4Output.Item(
                id,
                item.priority(),
                item.guidanceType(),
                item.category(),
                item.subProblemNo(),
                item.title(),
                item.currentStateMarkdown(),
                item.rationaleMarkdown(),
                item.guidanceMarkdown(),
                item.applicabilityMarkdown(),
                item.targetLocation(),
                item.evidenceQuotes(),
                item.acceptanceCriteriaMarkdown(),
                item.evidenceChain()
        );
    }

    private String applicability(String guidanceType, String category) {
        String target = switch (nullSafe(category).toUpperCase(Locale.ROOT)) {
            case "MODEL", "SOLUTION" -> "当前模型与求解论证";
            case "VALIDATION", "SENSITIVITY" -> "当前结果验证与适用边界";
            case "DATA" -> "当前数据来源与处理说明";
            case "WRITING", "FIGURE", "CITATION", "APPENDIX" -> "当前论文表达与规范";
            default -> "当前论文版本";
        };
        return switch (guidanceType) {
            case "REQUIRED_FIX" -> "该项针对已有原文和评审证据确认的错误或缺失，直接适用于" + target + "。";
            case "COMPLETENESS_ENHANCEMENT" -> "该项用于补齐" + target + "，不要求替换已有合理做法。";
            default -> "该项是可选探索方向，应结合时间、数据和现有模型目标决定是否采用，不代表唯一正确路径。";
        };
    }

    private String cleanGuidance(String markdown) {
        String value = nullSafe(markdown).strip();
        value = value.replaceAll(
                "(?ms)^\\s*#{1,6}\\s*(?:\\d+[.、]?\\s*)?"
                        + "(?:问题现状|当前论文事实|为什么值得处理|诊断分析)\\s*\\n+"
                        + ".*?(?=^\\s*#{1,6}\\s*|\\z)",
                ""
        );
        value = value.replaceFirst(
                "(?m)^\\s*#{1,6}\\s*(?:\\d+[.、]?\\s*)?(?:补充完善指引|建议补充的方面|改进方向)\\s*\\n+",
                ""
        );
        return value.strip();
    }

    private boolean guidanceMatchesFinding(
            String guidance,
            ReviewEvidenceSnapshot.Finding finding
    ) {
        if (finding == null) return true;
        Set<String> findingTopics = topicFamilies(topicGroups(finding.statement()));
        if (findingTopics.isEmpty()) return true;
        Set<String> guidanceTopics = topicFamilies(topicGroups(guidance));
        if (guidanceTopics.isEmpty()) return true;
        return findingTopics.containsAll(guidanceTopics);
    }

    private boolean knowledgeMatchesTopic(
            GroundedSuggestionV4Output.KnowledgeBasis basis,
            String topic
    ) {
        String knowledge = nullSafe(basis.title()) + " "
                + nullSafe(basis.section()) + " "
                + nullSafe(basis.applicabilityMarkdown());
        Set<String> topicGroups = topicGroups(topic);
        Set<String> knowledgeGroups = topicGroups(knowledge);
        if (!topicGroups.isEmpty() && !knowledgeGroups.isEmpty()) {
            return topicGroups.stream().anyMatch(knowledgeGroups::contains);
        }
        String normalizedTopic = normalizeForMatch(topic);
        String normalizedKnowledge = normalizeForMatch(knowledge);
        for (String term : normalizedTopic.split(" ")) {
            if (term.length() >= 3 && normalizedKnowledge.contains(term)) return true;
        }
        return false;
    }

    private Set<String> topicGroups(String value) {
        String normalized = normalizeForMatch(value);
        Set<String> result = new LinkedHashSet<>();
        TOPIC_GROUPS.forEach((group, markers) -> {
            if (markers.stream().anyMatch(normalized::contains)) {
                result.add(group);
            }
        });
        return result;
    }

    private Set<String> topicFamilies(Set<String> groups) {
        Set<String> result = new LinkedHashSet<>();
        for (String group : groups) {
            result.add(switch (group) {
                case "NOTATION", "PARAMETER", "DIMENSION", "FORMULA" -> "FORMALIZATION";
                default -> group;
            });
        }
        return result;
    }

    private String normalizeForMatch(String value) {
        return nullSafe(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[#*_>`$\\\\{}()\\[\\]，。；：、,.!?！？]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String cleanReviewStatement(String statement) {
        return nullSafe(statement)
                .replaceAll("(?m)^\\s*(?:\\*\\*)?(?:评审结论|问题说明|评分影响)(?:\\*\\*)?\\s*[：:]\\s*", "")
                .replaceAll("(?m)^\\s*\\*\\*评分影响\\*\\*\\s*[：:].*$", "")
                .strip();
    }

    private String reviewCurrentState(String statement) {
        String value = nullSafe(statement).strip();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?s)(?:\\*\\*)?(?:评审结论|问题说明)(?:\\*\\*)?\\s*[：:]\\s*"
                        + "(.+?)(?=\\n\\s*\\n\\s*(?:\\*\\*)?(?:原因|评分影响)(?:\\*\\*)?\\s*[：:]|$)"
        ).matcher(value);
        if (matcher.find()) return matcher.group(1).strip();
        return cleanReviewStatement(value)
                .replaceAll(
                        "(?s)\\n\\s*\\n\\s*(?:\\*\\*)?(?:原因|评分影响)(?:\\*\\*)?\\s*[：:].*$",
                        ""
                )
                .strip();
    }

    private String topicTitle(String statement) {
        String text = cleanReviewStatement(statement)
                .replaceAll("\\s+", " ")
                .strip();
        if (text.isBlank()) return "";
        int end = text.length();
        for (String delimiter : List.of("。", "；", "，", "\n")) {
            int index = text.indexOf(delimiter);
            if (index > 0) end = Math.min(end, index);
        }
        return text.substring(0, end).strip();
    }

    private String suggestionFingerprint(GroundedSuggestionV4Output.Item item) {
        List<String> reviewIds = item.evidenceChain() == null
                || item.evidenceChain().reviewFindingIds() == null
                ? List.of()
                : item.evidenceChain().reviewFindingIds();
        List<String> paperIds = item.evidenceChain() == null
                || item.evidenceChain().paperEvidenceIds() == null
                ? List.of()
                : item.evidenceChain().paperEvidenceIds();
        return item.guidanceType() + "\0"
                + item.category() + "\0"
                + String.join(",", reviewIds) + "\0"
                + String.join(",", paperIds);
    }

    private boolean containsForbiddenPhrase(String markdown) {
        return FORBIDDEN_PHRASES.stream().anyMatch(markdown::contains);
    }

    private String sanitizeKnowledgeMarkdown(String value) {
        String markdown = nullSafe(value);
        markdown = EXTERNAL_MARKDOWN_IMAGE.matcher(markdown).replaceAll("");
        markdown = EXTERNAL_HTML_IMAGE.matcher(markdown).replaceAll("");
        return markdown
                .replaceAll("(?m)^\\s*<参考知识事实[^>]*>\\s*$", "")
                .replaceAll("(?m)^\\s*</参考知识事实>\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private String normalizePriority(String value) {
        return PRIORITIES.containsKey(value) ? value : "P2";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算建议证据摘要", exception);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
