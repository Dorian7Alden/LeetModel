package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubTaskEvaluationResultDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.TaskPlanResultDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.config.ReviewV3Properties;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.entity.ReviewTaskLog;
import com.leetmodel.review.entity.PaperParseArtifact;
import com.leetmodel.review.parse.PaperParseService;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import com.leetmodel.review.parse.v2.PaperParseV2Parser;
import com.leetmodel.review.service.ReviewTaskLogService;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 第三代深度证据化 AI 论文评审工作流（DEEP_EVIDENCE_REVIEW_V3）。
 * 基于 PAPER_DOCUMENT_V2，双阶段解耦流水线，并行消费，五维确定性合成。
 */
@Slf4j
@Component
public class DeepEvidenceReviewV3Workflow implements ReviewWorkflow {

    public static final String VERSION_CODE = "DEEP_EVIDENCE_REVIEW_V3";
    public static final long VERSION_ID = 3L;
    public static final String RESULT_SCHEMA_VERSION = "DEEP_EVIDENCE_REVIEW_V3";
    public static final String SCORING_RULE_VERSION = DeepEvidenceReviewV3Reducer.SCORING_RULE_VERSION;

    private final PaperParseService parseService;
    private final ProblemFeignClient problemFeignClient;
    private final ObjectMapper objectMapper;
    private final ReviewTaskLogService logService;
    private final Phase1StructuralReviewOperator phase1Operator;
    private final TaskPlannerOperator taskPlannerOperator;
    private final SubTaskEvaluationWorker subTaskWorker;
    private final DeepEvidenceReviewV3Reducer reducer;
    private final Executor subTaskExecutor;
    private final ReviewV3Properties properties;
    private final String promptSnapshot;

    public DeepEvidenceReviewV3Workflow(
            PaperParseService parseService,
            ProblemFeignClient problemFeignClient,
            ObjectMapper objectMapper,
            ReviewTaskLogService logService,
            Phase1StructuralReviewOperator phase1Operator,
            TaskPlannerOperator taskPlannerOperator,
            SubTaskEvaluationWorker subTaskWorker,
            DeepEvidenceReviewV3Reducer reducer,
            @Qualifier("reviewSubTaskExecutor") Executor subTaskExecutor,
            ReviewV3Properties properties) {
        this.parseService = parseService;
        this.problemFeignClient = problemFeignClient;
        this.objectMapper = objectMapper;
        this.logService = logService;
        this.phase1Operator = phase1Operator;
        this.taskPlannerOperator = taskPlannerOperator;
        this.subTaskWorker = subTaskWorker;
        this.reducer = reducer;
        this.subTaskExecutor = subTaskExecutor;
        this.properties = properties;
        this.promptSnapshot = PromptTemplateRenderer.loadClasspathPrompt("prompts/phase1-structural-review.st");
    }

    @Override
    public String versionCode() {
        return VERSION_CODE;
    }

    @Override
    public Long versionId() {
        return VERSION_ID;
    }

    @Override
    public String currentPrompt() {
        return promptSnapshot;
    }

    @Override
    public ReviewWorkflowResult execute(ReviewTask task, SubmissionReviewDTO submission) throws Exception {
        // 1. 确保 PAPER_DOCUMENT_V2 解析产物可用
        ReviewTaskLog parseLog = logService.start(task, "ENSURE_PARSE_V2", "确保第二代高保真 PDF 解析产物",
                "workflow=" + PaperParseV2Parser.WORKFLOW_VERSION);
        PaperParseDTO parse;
        try {
            parse = parseService.ensure(submission.getId(), PaperParseV2Parser.WORKFLOW_VERSION);
            logService.succeed(parseLog, "artifactId=" + parse.getArtifactId() + ",status=" + parse.getStatus(), null);
        } catch (RuntimeException exception) {
            logService.fail(parseLog, exception);
            throw exception;
        }

        if (!("SUCCESS".equals(parse.getStatus()) || "PARTIAL_SUCCESS".equals(parse.getStatus()))) {
            throw new IllegalArgumentException("V3 评审要求可用的 PAPER_DOCUMENT_V2 产物");
        }

        PaperDocumentV2 document = objectMapper.readValue(parse.getDocumentJson(), PaperDocumentV2.class);
        if (document.metadata() == null || document.metadata().totalPages() < 1) {
            throw new IllegalArgumentException("PDF 解析质量未达到 V3 最低门槛");
        }

        ProblemContextDTO problem = requiredProblem(submission.getProblemId());

        // 2. 阶段一：结构规范性审查 (静态审查，免深度推理)
        ReviewTaskLog phase1Log = logService.start(task, "PHASE1_STRUCTURAL_REVIEW", "执行阶段一结构规范性静态审查", "slices=4");
        Phase1StructuralReviewResultDTO phase1Result;
        try {
            phase1Result = phase1Operator.execute(task, document);
            logService.succeed(phase1Log, "phase1Score=" + phase1Result.getScore(), null);
        } catch (RuntimeException exception) {
            logService.fail(phase1Log, exception);
            throw exception;
        }

        // 3. 阶段二步骤 2.1：动态任务规划
        ReviewTaskLog plannerLog = logService.start(task, "PHASE2_TASK_PLANNING", "执行阶段二动态任务规划", "questionCount=" + problem.getTitle());
        TaskPlanResultDTO taskPlan;
        try {
            taskPlan = taskPlannerOperator.plan(task, problem, document);
            logService.succeed(plannerLog, "plannedTasks=" + taskPlan.getTasks().size(), null);
        } catch (RuntimeException exception) {
            logService.fail(plannerLog, exception);
            throw exception;
        }

        // 4. 阶段二步骤 2.2 & 2.3：子任务并发消费
        ReviewTaskLog parallelLog = logService.start(task, "PHASE2_PARALLEL_EXECUTION", "执行阶段二子任务并发推理", "taskCount=" + taskPlan.getTasks().size());
        List<SubTaskEvaluationResultDTO> subTaskResults;
        try {
            subTaskResults = executeParallelSubTasks(task, taskPlan.getTasks(), document, problem);
            logService.succeed(parallelLog, "completedSubTasks=" + subTaskResults.size(), null);
        } catch (Exception exception) {
            logService.fail(parallelLog, exception);
            throw exception;
        }

        // 5. 阶段三：纯 Java 内存 Reducer 终态维度合成与算术求和
        ReviewTaskLog reduceLog = logService.start(task, "REDUCE_V3_RESULTS", "执行阶段一与阶段二结果确定性汇聚与算术断言", "dimensions=5");
        DeepEvidenceReviewV3Output finalOutput;
        try {
            finalOutput = reducer.reduce(phase1Result, subTaskResults, problem, document);
            logService.succeed(reduceLog, "finalScore=" + finalOutput.getScore(), null);
        } catch (Exception exception) {
            logService.fail(reduceLog, exception);
            throw exception;
        }

        String resultJson = objectMapper.writeValueAsString(finalOutput);
        return new ReviewWorkflowResult(
                finalOutput.getScore(),
                resultJson,
                "deepseek-v3-multi-stage",
                "call-v3-" + task.getId(),
                parse.getArtifactId()
        );
    }

    private List<SubTaskEvaluationResultDTO> executeParallelSubTasks(
            ReviewTask task, List<SubTaskPlanDTO> plans, PaperDocumentV2 document, ProblemContextDTO problem) {
        List<CompletableFuture<SubTaskEvaluationResultDTO>> futures = new ArrayList<>();
        for (var plan : plans) {
            CompletableFuture<SubTaskEvaluationResultDTO> future = CompletableFuture.supplyAsync(
                    () -> subTaskWorker.execute(task, plan, document, problem),
                    subTaskExecutor
            );
            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allOf.get(properties.getPhase2TimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("阶段二子任务并发等待超时，收集已完成或部分降级结果: {}", exception.getMessage());
        }

        List<SubTaskEvaluationResultDTO> results = new ArrayList<>();
        for (int i = 0; i < plans.size(); i++) {
            CompletableFuture<SubTaskEvaluationResultDTO> future = futures.get(i);
            SubTaskPlanDTO plan = plans.get(i);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    results.add(fallbackForWorker(plan, e.getMessage()));
                }
            } else {
                results.add(fallbackForWorker(plan, "并发执行超时或异常中断"));
            }
        }
        return results;
    }

    private SubTaskEvaluationResultDTO fallbackForWorker(SubTaskPlanDTO plan, String error) {
        return SubTaskEvaluationResultDTO.builder()
                .taskId(plan.getTaskId())
                .taskType(plan.getTaskType())
                .targetQuestionNo(plan.getTargetQuestionNo())
                .executionStatus("DEGRADED")
                .maxScore(BigDecimal.valueOf(15.0))
                .score(BigDecimal.valueOf(9.0))
                .evaluationSummary("子任务容错降级: " + error)
                .aspectScores(List.of())
                .observations(List.of())
                .findings(List.of())
                .build();
    }

    private ProblemContextDTO requiredProblem(Long problemId) {
        Result<ProblemContextDTO> result = problemFeignClient.getProblemContext(problemId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("problem-service 暂不可用");
        }
        return result.getData();
    }
}
