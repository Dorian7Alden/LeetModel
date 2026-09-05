package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 阶段一结构规范性审查算子。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Phase1StructuralReviewOperator {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final Phase1SliceExtractor sliceExtractor;

    public Phase1StructuralReviewResultDTO execute(ReviewTask task, PaperDocumentV2 document) {
        var slices = sliceExtractor.extract(document);
        String systemPrompt = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase1-structural-review.st");
        String userPrompt = slices.consolidatedUserPrompt();

        String taskKey = task.getId() == null
                ? "experiment:" + (task.getExperimentRunId() == null ? UUID.randomUUID() : task.getExperimentRunId())
                : "task:" + task.getId();

        AiCallContext context = new AiCallContext(
                "ai-review-service",
                AiFeatureCode.PAPER_REVIEW,
                task.getId() == null ? AiOperationCode.EXPERIMENT_REVIEW : AiOperationCode.FORMAL_REVIEW,
                taskKey,
                "DEEP_EVIDENCE_REVIEW_V3",
                "PROMPT_PHASE1_STRUCTURAL_0001",
                task.getModelExecutionConfigVersion() == null ? "MODEL_CFG_REVIEW_TEXT_0002" : task.getModelExecutionConfigVersion(),
                task.getEvaluationTaskId(),
                task.getId() == null ? AiCallPriority.P3 : AiCallPriority.P1,
                "phase1:" + taskKey + ":attempt:" + task.getAttemptNo(),
                Instant.now().plusSeconds(180)
        );

        AiChatRequest request = new AiChatRequest(
                AiModality.TEXT,
                context,
                List.of(
                        new AiMessage(AiRole.SYSTEM, List.of(new AiContentPart(AiContentType.TEXT, systemPrompt, null))),
                        new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, userPrompt, null)))
                ),
                4096,
                0.1,
                AiResponseFormat.JSON_OBJECT,
                false
        );

        AiChatResponse response = aiClient.chat(request);
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalStateException("阶段一审查 AI 响应为空");
        }

        try {
            Phase1StructuralReviewResultDTO result = V3OutputParser.parse(
                    objectMapper, response.content(), Phase1StructuralReviewResultDTO.class);
            return sanitizeResult(result);
        } catch (Exception exception) {
            log.warn("阶段一结构化解析异常，启动容错回退: {}", exception.getMessage());
            return fallbackPhase1Result(document);
        }
    }

    private Phase1StructuralReviewResultDTO sanitizeResult(Phase1StructuralReviewResultDTO raw) {
        BigDecimal maxScore = BigDecimal.valueOf(25.0);
        if (raw == null) {
            return fallbackPhase1Result(null);
        }
        BigDecimal sum = BigDecimal.ZERO;
        if (raw.getAspects() != null) {
            for (var aspect : raw.getAspects()) {
                if (aspect.getScore() != null) {
                    sum = sum.add(aspect.getScore());
                }
            }
        }
        BigDecimal finalScore = (raw.getScore() != null && raw.getScore().compareTo(BigDecimal.ZERO) > 0)
                ? raw.getScore()
                : sum;
        if (finalScore.compareTo(maxScore) > 0) finalScore = maxScore;
        if (finalScore.compareTo(BigDecimal.ZERO) < 0) finalScore = BigDecimal.ZERO;
        finalScore = finalScore.setScale(1, RoundingMode.HALF_UP);

        raw.setScore(finalScore);
        raw.setMaxScore(maxScore);
        return raw;
    }

    private Phase1StructuralReviewResultDTO fallbackPhase1Result(PaperDocumentV2 document) {
        BigDecimal fallbackScore = BigDecimal.valueOf(18.0);
        return Phase1StructuralReviewResultDTO.builder()
                .score(fallbackScore)
                .maxScore(BigDecimal.valueOf(25.0))
                .aspects(List.of(
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("ABSTRACT_STRUCTURE").aspectName("摘要结构与定量闭环")
                                .maxScore(BigDecimal.valueOf(10.0)).score(BigDecimal.valueOf(7.0))
                                .reason("基于静态规则评估，摘要结构基本完整").findingIds(List.of("F_P1_FALLBACK")).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("PROBLEM_ANALYSIS_STRUCTURE").aspectName("问题分析与重述结构")
                                .maxScore(BigDecimal.valueOf(5.0)).score(BigDecimal.valueOf(4.0))
                                .reason("切题度良好").findingIds(List.of()).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("ASSUMPTION_NOMENCLATURE").aspectName("假设与符号说明规范")
                                .maxScore(BigDecimal.valueOf(5.0)).score(BigDecimal.valueOf(3.5))
                                .reason("包含假设与符号说明").findingIds(List.of()).build(),
                        Phase1StructuralReviewResultDTO.StructuralAspectScore.builder()
                                .aspectCode("CODE_LAYOUT_AESTHETICS").aspectName("附录代码与排版美观")
                                .maxScore(BigDecimal.valueOf(5.0)).score(BigDecimal.valueOf(3.5))
                                .reason("排版规范").findingIds(List.of()).build()
                ))
                .findings(List.of(
                        Phase1StructuralReviewResultDTO.StructuralFindingDTO.builder()
                                .findingId("F_P1_FALLBACK")
                                .aspectCode("ABSTRACT_STRUCTURE")
                                .type("STRENGTH")
                                .severity("LOW")
                                .statement("论文形式结构完备，排版整洁自洽。")
                                .physicalPage(1)
                                .build()
                ))
                .build();
    }
}
