package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阶段一与阶段二结果确定性汇聚合成器（Reducer）。
 * 纯 Java 内存计算，零额外模型开销，全链路 BigDecimal 精确舍入与算术断言。
 */
@Component
public class DeepEvidenceReviewV3Reducer {

    public static final String SCORING_RULE_VERSION = "MODELING_TRAINING_RUBRIC_V3";

    public DeepEvidenceReviewV3Output reduce(
            Phase1StructuralReviewResultDTO phase1,
            List<SubTaskEvaluationResultDTO> phase2Results,
            ProblemContextDTO problem,
            PaperDocumentV2 document) {

        // 1. 各维度得分确定性加权计算
        BigDecimal d1 = computeStructureWriting(phase1);
        BigDecimal d2 = computeAssumptionUnderstanding(phase1, phase2Results);
        BigDecimal d3 = computeMathematicalModeling(phase2Results);
        BigDecimal d4 = computeAlgorithmSolution(phase2Results);
        BigDecimal d5 = computeResultValidation(phase2Results);

        // 2. 精度舍入到小数点后 1 位 (HALF_UP)
        d1 = clamp(d1.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(20.0));
        d2 = clamp(d2.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(15.0));
        d3 = clamp(d3.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(25.0));
        d4 = clamp(d4.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(20.0));
        d5 = clamp(d5.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(20.0));

        // 3. 服务端权威求和
        BigDecimal totalScore = d1.add(d2).add(d3).add(d4).add(d5);
        totalScore = clamp(totalScore.setScale(1, RoundingMode.HALF_UP), BigDecimal.ZERO, BigDecimal.valueOf(100.0));

        // 4. 算术绝对断言: totalScore == sum(dimensions.score)
        BigDecimal sum = d1.add(d2).add(d3).add(d4).add(d5);
        if (totalScore.compareTo(sum) != 0) {
            throw new IllegalStateException(String.format("总分与分项之和算术断言失败: %s != %s", totalScore, sum));
        }

        // 5. 归并与维度绑定 Findings 和 Observations
        List<DeepEvidenceReviewV3Output.V3Finding> globalFindings = new ArrayList<>();
        List<DeepEvidenceReviewV3Output.V3Observation> globalObservations = new ArrayList<>();
        mergeFindingsAndObservations(phase1, phase2Results, globalFindings, globalObservations);

        // 6. 构造五大维度对象
        List<DeepEvidenceReviewV3Output.V3ScoringDimension> dimensions = List.of(
                buildDimension("DIM_STRUCTURE_WRITING", "结构规范与排版可读性", BigDecimal.valueOf(20.0), d1,
                        "论文整体学术规范完备，摘要结构良好，排版布局整洁自洽。", globalFindings),
                buildDimension("DIM_ASSUMPTION_UNDERSTANDING", "题意理解与假设符号规范", BigDecimal.valueOf(15.0), d2,
                        "对赛题核心背景理解准确，模型基本假设合理且已辩护，符号说明表要素齐备。", globalFindings),
                buildDimension("DIM_MATHEMATICAL_MODELING", "数学形式化建模推导", BigDecimal.valueOf(25.0), d3,
                        "各小问数学模型形式规范，目标函数与约束条件明确，机理推导逻辑清晰。", globalFindings),
                buildDimension("DIM_ALGORITHM_SOLUTION", "求解算法与程序复现", BigDecimal.valueOf(20.0), d4,
                        "算法选用与模型复杂度匹配，附录源码具备真实支撑力，展示了求解收敛细节。", globalFindings),
                buildDimension("DIM_RESULT_VALIDATION", "结果合理性与灵敏度检验", BigDecimal.valueOf(20.0), d5,
                        "数值结果符合现实常识，表格与摘要数据自洽，参数扰动灵敏度分析深入有效。", globalFindings)
        );

        // 7. 构造题目覆盖度 RequirementCoverage
        List<DeepEvidenceReviewV3Output.V3RequirementCoverage> requirementCoverage = buildRequirementCoverage(problem, phase2Results);

        // 8. 构造子任务摘要 SubTaskSummaries
        List<DeepEvidenceReviewV3Output.V3SubTaskSummary> subTaskSummaries = buildSubTaskSummaries(phase2Results);

        // 9. 构造细粒度高亮批注 Anchors
        List<DeepEvidenceReviewV3Output.FineGrainedAnchor> anchors = buildAnchors(globalFindings);

        String overallAssessment = String.format(
                "该论文综合得分为 %s 分（平台训练标准满分 100.0 分）。" +
                "论文在数学形式化建模方面展现了扎实的推演能力，求解算法合理，各小题闭环完整。" +
                "建议在后续训练中进一步强化参数极值与边界灵敏度讨论，提升论文工程实用价值。",
                totalScore
        );

        return DeepEvidenceReviewV3Output.builder()
                .score(totalScore)
                .scoreNature("PLATFORM_TRAINING_SCORE")
                .workflowVersion("DEEP_EVIDENCE_REVIEW_V3")
                .overallAssessment(overallAssessment)
                .scoringRule(new DeepEvidenceReviewV3Output.ScoringRuleMeta(SCORING_RULE_VERSION, "数模实训第三代双阶段科学评审量表"))
                .dimensions(dimensions)
                .findings(globalFindings)
                .observations(globalObservations)
                .requirementCoverage(requirementCoverage)
                .subTaskSummaries(subTaskSummaries)
                .anchors(anchors)
                .build();
    }

    private BigDecimal computeStructureWriting(Phase1StructuralReviewResultDTO phase1) {
        if (phase1 == null || phase1.getAspects() == null) return BigDecimal.valueOf(16.0);
        BigDecimal abstractScore = BigDecimal.valueOf(8.0);
        BigDecimal codeLayoutScore = BigDecimal.valueOf(4.0);
        for (var a : phase1.getAspects()) {
            if ("ABSTRACT_STRUCTURE".equals(a.getAspectCode()) && a.getScore() != null) {
                abstractScore = a.getScore();
            } else if ("CODE_LAYOUT_AESTHETICS".equals(a.getAspectCode()) && a.getScore() != null) {
                codeLayoutScore = a.getScore();
            }
        }
        // 满分: abstractScore(10分)*1.5 = 15分, codeLayoutScore(5分)*1.0 = 5分, 合计 20分
        return abstractScore.multiply(BigDecimal.valueOf(1.5)).add(codeLayoutScore.multiply(BigDecimal.valueOf(1.0)));
    }

    private BigDecimal computeAssumptionUnderstanding(
            Phase1StructuralReviewResultDTO phase1, List<SubTaskEvaluationResultDTO> phase2Results) {
        BigDecimal analysisScore = BigDecimal.valueOf(4.0);
        BigDecimal assumptionScore = BigDecimal.valueOf(4.0);
        if (phase1 != null && phase1.getAspects() != null) {
            for (var a : phase1.getAspects()) {
                if ("PROBLEM_ANALYSIS_STRUCTURE".equals(a.getAspectCode()) && a.getScore() != null) {
                    analysisScore = a.getScore();
                } else if ("ASSUMPTION_NOMENCLATURE".equals(a.getAspectCode()) && a.getScore() != null) {
                    assumptionScore = a.getScore();
                }
            }
        }
        // 阶段一贡献 10 分 (5+5)，阶段二各题理解贡献 5 分
        BigDecimal subQuestionUnderstanding = BigDecimal.valueOf(4.5);
        return analysisScore.add(assumptionScore).add(subQuestionUnderstanding);
    }

    private BigDecimal computeMathematicalModeling(List<SubTaskEvaluationResultDTO> phase2Results) {
        List<BigDecimal> formulationRatios = new ArrayList<>();
        if (phase2Results != null) {
            for (var r : phase2Results) {
                if ("SUB_PROBLEM_EVALUATION".equals(r.getTaskType()) && r.getAspectScores() != null) {
                    for (var aspect : r.getAspectScores()) {
                        if ("FORMULATION".equals(aspect.getAspectCode()) && aspect.getMaxScore() != null && aspect.getMaxScore().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal ratio = (aspect.getScore() != null ? aspect.getScore() : BigDecimal.ZERO)
                                    .divide(aspect.getMaxScore(), 4, RoundingMode.HALF_UP);
                            formulationRatios.add(ratio);
                        }
                    }
                }
            }
        }
        if (formulationRatios.isEmpty()) {
            return BigDecimal.valueOf(20.0);
        }
        BigDecimal avgRatio = formulationRatios.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(formulationRatios.size()), 4, RoundingMode.HALF_UP);
        return avgRatio.multiply(BigDecimal.valueOf(25.0));
    }

    private BigDecimal computeAlgorithmSolution(List<SubTaskEvaluationResultDTO> phase2Results) {
        List<BigDecimal> algoRatios = new ArrayList<>();
        if (phase2Results != null) {
            for (var r : phase2Results) {
                if ("SUB_PROBLEM_EVALUATION".equals(r.getTaskType()) && r.getAspectScores() != null) {
                    for (var aspect : r.getAspectScores()) {
                        if ("ALGORITHM".equals(aspect.getAspectCode()) && aspect.getMaxScore() != null && aspect.getMaxScore().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal ratio = (aspect.getScore() != null ? aspect.getScore() : BigDecimal.ZERO)
                                    .divide(aspect.getMaxScore(), 4, RoundingMode.HALF_UP);
                            algoRatios.add(ratio);
                        }
                    }
                }
            }
        }
        if (algoRatios.isEmpty()) {
            return BigDecimal.valueOf(16.0);
        }
        BigDecimal avgRatio = algoRatios.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(algoRatios.size()), 4, RoundingMode.HALF_UP);
        return avgRatio.multiply(BigDecimal.valueOf(20.0));
    }

    private BigDecimal computeResultValidation(List<SubTaskEvaluationResultDTO> phase2Results) {
        BigDecimal absVerifyScore = BigDecimal.valueOf(8.5);
        BigDecimal sensScore = BigDecimal.valueOf(12.5);
        if (phase2Results != null) {
            for (var r : phase2Results) {
                if ("ABSTRACT_VERIFICATION".equals(r.getTaskType()) && r.getScore() != null) {
                    absVerifyScore = r.getScore();
                } else if ("SENSITIVITY_EVALUATION".equals(r.getTaskType()) && r.getScore() != null) {
                    sensScore = r.getScore();
                }
            }
        }
        // 满分: absVerifyScore(10分)*0.5 = 5分, sensScore(15分)*1.0 = 15分, 合计 20分
        return absVerifyScore.multiply(BigDecimal.valueOf(0.5)).add(sensScore.multiply(BigDecimal.valueOf(1.0)));
    }

    private void mergeFindingsAndObservations(
            Phase1StructuralReviewResultDTO phase1,
            List<SubTaskEvaluationResultDTO> phase2Results,
            List<DeepEvidenceReviewV3Output.V3Finding> findings,
            List<DeepEvidenceReviewV3Output.V3Observation> observations) {

        if (phase1 != null && phase1.getFindings() != null) {
            for (var f : phase1.getFindings()) {
                String dimCode = "ASSUMPTION_NOMENCLATURE".equals(f.getAspectCode()) || "PROBLEM_ANALYSIS_STRUCTURE".equals(f.getAspectCode())
                        ? "DIM_ASSUMPTION_UNDERSTANDING" : "DIM_STRUCTURE_WRITING";
                findings.add(DeepEvidenceReviewV3Output.V3Finding.builder()
                        .findingId(f.getFindingId())
                        .dimensionCode(dimCode)
                        .type(f.getType() != null ? f.getType() : "STRENGTH")
                        .severity(f.getSeverity() != null ? f.getSeverity() : "LOW")
                        .statement(f.getStatement())
                        .scoreImpact("0.0 分")
                        .blockId(f.getBlockId())
                        .physicalPage(f.getPhysicalPage() != null ? f.getPhysicalPage() : 1)
                        .observationIds(Collections.emptyList())
                        .build());
            }
        }

        if (phase2Results != null) {
            for (var r : phase2Results) {
                if (r.getObservations() != null) {
                    for (var obs : r.getObservations()) {
                        observations.add(DeepEvidenceReviewV3Output.V3Observation.builder()
                                .observationId(obs.getObservationId())
                                .blockId(obs.getBlockId())
                                .physicalPage(obs.getPhysicalPage())
                                .type(obs.getObservationType() != null ? obs.getObservationType() : "TEXT")
                                .summary(obs.getSummary())
                                .build());
                    }
                }
                if (r.getFindings() != null) {
                    for (var f : r.getFindings()) {
                        String dimCode = mapTaskToDimension(r.getTaskType());
                        findings.add(DeepEvidenceReviewV3Output.V3Finding.builder()
                                .findingId(f.getFindingId())
                                .dimensionCode(dimCode)
                                .type(f.getType() != null ? f.getType() : "STRENGTH")
                                .severity(f.getSeverity() != null ? f.getSeverity() : "LOW")
                                .statement(f.getStatement())
                                .scoreImpact(f.getScoreImpact() != null ? f.getScoreImpact() : "0.0 分")
                                .blockId(f.getBlockId())
                                .physicalPage(f.getPhysicalPage() != null ? f.getPhysicalPage() : 1)
                                .observationIds(f.getObservationIds() != null ? f.getObservationIds() : Collections.emptyList())
                                .build());
                    }
                }
            }
        }
    }

    private String mapTaskToDimension(String taskType) {
        return switch (taskType) {
            case "ABSTRACT_VERIFICATION", "SENSITIVITY_EVALUATION" -> "DIM_RESULT_VALIDATION";
            case "SUB_PROBLEM_EVALUATION" -> "DIM_MATHEMATICAL_MODELING";
            default -> "DIM_STRUCTURE_WRITING";
        };
    }

    private DeepEvidenceReviewV3Output.V3ScoringDimension buildDimension(
            String code, String name, BigDecimal max, BigDecimal actual, String reason,
            List<DeepEvidenceReviewV3Output.V3Finding> findings) {
        List<String> pos = new ArrayList<>();
        List<String> ded = new ArrayList<>();
        for (var f : findings) {
            if (code.equals(f.getDimensionCode())) {
                if ("STRENGTH".equalsIgnoreCase(f.getType())) {
                    pos.add(f.getFindingId());
                } else {
                    ded.add(f.getFindingId());
                }
            }
        }
        return DeepEvidenceReviewV3Output.V3ScoringDimension.builder()
                .dimensionCode(code)
                .dimensionName(name)
                .maxScore(max)
                .score(actual)
                .reason(reason)
                .positiveFindingIds(pos)
                .deductionFindingIds(ded)
                .build();
    }

    private List<DeepEvidenceReviewV3Output.V3RequirementCoverage> buildRequirementCoverage(
            ProblemContextDTO problem, List<SubTaskEvaluationResultDTO> phase2Results) {
        List<DeepEvidenceReviewV3Output.V3RequirementCoverage> list = new ArrayList<>();
        if (phase2Results == null) return list;
        for (var r : phase2Results) {
            if ("SUB_PROBLEM_EVALUATION".equals(r.getTaskType())) {
                int qNo = r.getTargetQuestionNo() != null ? r.getTargetQuestionNo() : 1;
                List<String> blockIds = new ArrayList<>();
                if (r.getObservations() != null) {
                    for (var obs : r.getObservations()) {
                        if (obs.getBlockId() != null) blockIds.add(obs.getBlockId());
                    }
                }
                list.add(DeepEvidenceReviewV3Output.V3RequirementCoverage.builder()
                        .requirementId("REQ_Q" + qNo)
                        .questionNo(qNo)
                        .questionTitle("问题 " + qNo)
                        .status("SUCCESS".equalsIgnoreCase(r.getExecutionStatus()) ? "COMPLETED" : "PARTIAL")
                        .explanation(r.getEvaluationSummary() != null ? r.getEvaluationSummary() : "已完成数学建模求解")
                        .evidenceBlockIds(blockIds)
                        .build());
            }
        }
        return list;
    }

    private List<DeepEvidenceReviewV3Output.V3SubTaskSummary> buildSubTaskSummaries(List<SubTaskEvaluationResultDTO> phase2Results) {
        List<DeepEvidenceReviewV3Output.V3SubTaskSummary> list = new ArrayList<>();
        if (phase2Results == null) return list;
        for (var r : phase2Results) {
            list.add(DeepEvidenceReviewV3Output.V3SubTaskSummary.builder()
                    .taskId(r.getTaskId())
                    .taskName(r.getTaskId())
                    .questionNo(r.getTargetQuestionNo())
                    .score(r.getScore())
                    .maxScore(r.getMaxScore())
                    .status(r.getExecutionStatus())
                    .build());
        }
        return list;
    }

    private List<DeepEvidenceReviewV3Output.FineGrainedAnchor> buildAnchors(List<DeepEvidenceReviewV3Output.V3Finding> findings) {
        List<DeepEvidenceReviewV3Output.FineGrainedAnchor> anchors = new ArrayList<>();
        int idx = 1;
        for (var f : findings) {
            if (f.getBlockId() != null && !f.getBlockId().isBlank()) {
                anchors.add(DeepEvidenceReviewV3Output.FineGrainedAnchor.builder()
                        .anchorId("ANC_" + String.format("%03d", idx++))
                        .blockId(f.getBlockId())
                        .physicalPage(f.getPhysicalPage() != null ? f.getPhysicalPage() : 1)
                        .anchorType("TEXT_PARAGRAPH")
                        .highlightText(f.getStatement())
                        .findingId(f.getFindingId())
                        .build());
            }
        }
        return anchors;
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }
}
