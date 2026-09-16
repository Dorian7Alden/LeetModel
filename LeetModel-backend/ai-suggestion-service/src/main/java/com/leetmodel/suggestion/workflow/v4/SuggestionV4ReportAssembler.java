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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将 V3 候选建议确定性转换为 V4 三类方向建议。 */
@Component
public class SuggestionV4ReportAssembler {

    private static final int MAX_QUOTE_LENGTH = 1200;
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

        List<GroundedSuggestionV4Output.Item> normalized = new ArrayList<>();
        int sequence = 1;
        for (GroundedSuggestionV4Output.Item item : items) {
            normalized.add(withId(item, "S-" + sequence++));
            if (normalized.size() == 10) break;
        }
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

        return new GroundedSuggestionV4Output(
                GroundedSuggestionV4Workflow.VERSION,
                GroundedSuggestionV4Workflow.RESULT_SCHEMA_VERSION,
                overallStrategy(source, normalized),
                priorities,
                List.copyOf(normalized),
                knowledgeBasis
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
        String guidanceType = guidanceType(source, reviewIds, reviewFindings);
        if ("REQUIRED_FIX".equals(guidanceType) && reviewIds.isEmpty()) return null;
        String priority = calibratedPriority(source.priority(), reviewIds, reviewFindings);

        List<GroundedSuggestionV4Output.EvidenceQuote> quotes = new ArrayList<>();
        int quoteSequence = 1;
        for (String blockId : paperIds) {
            quotes.add(toQuote("PE-" + quoteSequence++, paperBlocks.get(blockId)));
        }
        List<String> basisIds = matchedKnowledgeBasisIds(source, knowledgeBasis);
        if (basisIds.isEmpty()) return null;

        String guidance = soften(nullSafe(source.actionPlanMarkdown()), guidanceType);
        if (containsForbiddenPhrase(guidance)) return null;

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

        return new GroundedSuggestionV4Output.Item(
                source.suggestionId() == null ? "S-0" : source.suggestionId(),
                priority,
                guidanceType,
                source.category() == null ? "MODEL" : source.category(),
                source.subProblemNo(),
                source.title() == null ? "论文完善方向" : source.title(),
                markdownParagraph("当前论文事实", source.problemOrGap()),
                markdownParagraph("为什么值得处理", source.diagnosis()),
                guidance,
                applicability(guidanceType),
                targetLocation,
                List.copyOf(quotes),
                source.acceptanceCriteria() == null || source.acceptanceCriteria().isEmpty()
                        ? List.of("相关说明、证据和结论之间能够形成可复核的闭环。")
                        : source.acceptanceCriteria(),
                new GroundedSuggestionV4Output.EvidenceChain(
                        paperIds,
                        reviewIds,
                        basisIds
                )
        );
    }

    private String guidanceType(
            GroundedSuggestionV3Output.Item item,
            List<String> reviewIds,
            Map<String, ReviewEvidenceSnapshot.Finding> findings
    ) {
        if (!"CORRECTION".equals(item.type())) return "OPTIONAL_EXPLORATION";
        boolean highImpact = reviewIds.stream()
                .map(findings::get)
                .filter(java.util.Objects::nonNull)
                .map(ReviewEvidenceSnapshot.Finding::severity)
                .anyMatch(value -> Set.of("P0", "P1", "BLOCKING", "CRITICAL", "HIGH").contains(
                        nullSafe(value).toUpperCase(Locale.ROOT)
                ));
        return highImpact ? "REQUIRED_FIX" : "COMPLETENESS_ENHANCEMENT";
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
        return result;
    }

    private List<String> matchedKnowledgeBasisIds(
            GroundedSuggestionV3Output.Item source,
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
        return source.evidenceChain().knowledgeCitationIds().stream()
                .map(basisIdByCitationId::get)
                .filter(java.util.Objects::nonNull)
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
                    limit(citation.getContent(), 900),
                    citation.getApplicability()
            ));
        }
        return List.copyOf(result);
    }

    private GroundedSuggestionV4Output.EvidenceQuote toQuote(String evidenceId, JsonNode block) {
        String markdown = blockMarkdown(block);
        boolean truncated = markdown.length() > MAX_QUOTE_LENGTH;
        String safe = truncated ? markdown.substring(0, MAX_QUOTE_LENGTH) + "\n\n> ...（原文节选）" : markdown;
        return new GroundedSuggestionV4Output.EvidenceQuote(
                evidenceId,
                block.path("blockId").asText(),
                block.path("physicalPage").asInt(1),
                block.path("type").asText("PARAGRAPH"),
                safe,
                "sha256:" + sha256(markdown),
                truncated
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

    private String applicability(String guidanceType) {
        return switch (guidanceType) {
            case "REQUIRED_FIX" -> "该项针对可验证的错误或缺失，适用于当前论文版本。";
            case "COMPLETENESS_ENHANCEMENT" -> "该项用于补齐现有论证，不要求替换当前模型。";
            default -> "该项是可选探索方向，应结合时间、数据和现有模型目标决定是否采用。";
        };
    }

    private String markdownParagraph(String heading, String content) {
        return "### " + heading + "\n\n" + nullSafe(content);
    }

    private boolean containsForbiddenPhrase(String markdown) {
        return FORBIDDEN_PHRASES.stream().anyMatch(markdown::contains);
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

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
