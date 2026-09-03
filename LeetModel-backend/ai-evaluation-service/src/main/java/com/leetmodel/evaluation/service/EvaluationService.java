package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationCandidateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationRunDTO;
import com.leetmodel.common.api.dto.EvaluationSampleCreateDTO;
import com.leetmodel.common.api.dto.EvaluationSampleDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.EvaluationRawMetricsDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.messaging.internal.OperationAuditGovernanceProducer;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationSample;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.enums.EvaluationErrorCode;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import com.leetmodel.evaluation.messaging.EvaluationSlotReadyMessageService;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import com.leetmodel.evaluation.runner.EvaluationExperimentCommand;
import com.leetmodel.evaluation.runner.EvaluationExperimentOutcome;
import com.leetmodel.evaluation.runner.EvaluationExperimentRunner;
import com.leetmodel.evaluation.runner.EvaluationRunnerException;
import com.leetmodel.evaluation.runner.EvaluationRunnerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EvaluationService {

    private static final Set<String> RETRYABLE_FAILURES = Set.of("ENVIRONMENT", "CONFIGURATION");

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationSampleMapper sampleMapper;
    private final EvaluationTaskMapper taskMapper;
    private final EvaluationRunAttemptMapper runMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final AiGatewayFeignClient aiGatewayFeignClient;
    private final EvaluationRunnerRegistry runnerRegistry;
    private final EvaluationEstimateService estimateService;
    private final EvaluationPersistenceService persistenceService;
    private final EvaluationMetricsCalculator metricsCalculator;
    private final EvaluationMetricRegistry metricRegistry;
    private final EvaluationWeightSchemeService weightSchemeService;
    private final EvaluationScoreResultService scoreResultService;
    private final EvaluationCompletionPersistenceService completionPersistenceService;
    private final EvaluationSlotReadyMessageService readyMessageService;
    private final ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OperationAuditGovernanceProducer audit;

    /** 保留服务单元测试的构造契约。 */
    public EvaluationService(EvaluationDatasetMapper datasetMapper,
                             EvaluationSampleMapper sampleMapper,
                             EvaluationTaskMapper taskMapper,
                             EvaluationRunAttemptMapper runMapper,
                             SubmissionFeignClient submissionFeignClient,
                             AiGatewayFeignClient aiGatewayFeignClient,
                             EvaluationRunnerRegistry runnerRegistry,
                             EvaluationEstimateService estimateService,
                             EvaluationPersistenceService persistenceService,
                             EvaluationMetricsCalculator metricsCalculator,
                             EvaluationMetricRegistry metricRegistry,
                             EvaluationWeightSchemeService weightSchemeService,
                             EvaluationScoreResultService scoreResultService,
                             EvaluationCompletionPersistenceService completionPersistenceService,
                             ObjectMapper objectMapper) {
        this(datasetMapper, sampleMapper, taskMapper, runMapper, submissionFeignClient,
                aiGatewayFeignClient, runnerRegistry, estimateService, persistenceService,
                metricsCalculator, metricRegistry, weightSchemeService, scoreResultService,
                completionPersistenceService, null, objectMapper);
    }

    /** 创建后即锁定样本引用，MVP 不提供原地编辑。 */
    public EvaluationDatasetDTO createDataset(EvaluationDatasetCreateDTO request) {
        estimateService.requireDatasetSize(request.getSamples().size());
        String featureCode = request.getFeatureCode() == null || request.getFeatureCode().isBlank()
                ? "REVIEW" : request.getFeatureCode().trim();
        EvaluationExperimentRunner runner = runnerRegistry.require(featureCode);
        if ("REVIEW".equals(featureCode)) {
            Set<Long> references = new java.util.HashSet<>();
            for (EvaluationSampleCreateDTO input : request.getSamples()) {
                Long submissionId = runner.validateSample(requestPayload(featureCode, input)).submissionId();
                BusinessException.throwIf(submissionId == null || !references.add(submissionId),
                        EvaluationErrorCode.DUPLICATE_SAMPLE);
            }
        }
        List<ResolvedSample> resolved = new ArrayList<>();
        Set<Long> submissionIds = new java.util.HashSet<>();
        for (EvaluationSampleCreateDTO input : request.getSamples()) {
            ValidatedSamplePayload payload = runner.validateSample(requestPayload(featureCode, input));
            SubmissionReviewDTO submission = null;
            if ("REVIEW".equals(featureCode)) {
                BusinessException.throwIf(payload.submissionId() == null
                                || !submissionIds.add(payload.submissionId()),
                        EvaluationErrorCode.DUPLICATE_SAMPLE);
                submission = requiredSample(payload.submissionId());
                BusinessException.throwIf(!payload.submissionId().equals(submission.getId())
                                || submission.getTeamId() == null || submission.getProblemId() == null
                                || submission.getObjectName() == null || submission.getObjectName().isBlank(),
                        EvaluationErrorCode.SAMPLE_UNAVAILABLE);
            }
            resolved.add(new ResolvedSample(submission, payload, trimToNull(input.getNote())));
        }

        LocalDateTime now = LocalDateTime.now();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setFeatureCode(featureCode);
        dataset.setDatasetVersion(request.getDatasetVersion() == null || request.getDatasetVersion().isBlank()
                ? featureCode + "_DATASET_" + UUID.randomUUID().toString().replace("-", "")
                : request.getDatasetVersion().trim());
        dataset.setSampleSchemaVersion(resolved.get(0).payload().payloadSchemaVersion());
        dataset.setName(request.getName().trim());
        dataset.setDescription(trimToNull(request.getDescription()));
        dataset.setStatus("LOCKED");
        dataset.setSampleCount(resolved.size());
        dataset.setCreatedBy(request.getCreatedBy());
        dataset.setCreateTime(now);
        dataset.setUpdateTime(now);
        List<EvaluationSample> samples = new ArrayList<>();
        for (int index = 0; index < resolved.size(); index++) {
            ResolvedSample item = resolved.get(index);
            EvaluationSample sample = new EvaluationSample();
            sample.setSampleType(item.payload().sampleType());
            sample.setPayloadSchemaVersion(item.payload().payloadSchemaVersion());
            sample.setPayloadJson(item.payload().payloadJson());
            if (item.submission() != null) {
                sample.setSubmissionId(item.submission().getId());
                sample.setTeamId(item.submission().getTeamId());
                sample.setProblemId(item.submission().getProblemId());
            }
            sample.setSortOrder(index + 1);
            sample.setNote(item.note());
            sample.setCreateTime(now);
            sample.setUpdateTime(now);
            samples.add(sample);
        }
        persistenceService.createDataset(dataset, samples);
        return toDataset(dataset, samples);
    }

    public List<EvaluationDatasetDTO> listDatasets() {
        return datasetMapper.selectList(new LambdaQueryWrapper<EvaluationDataset>()
                        .orderByDesc(EvaluationDataset::getCreateTime))
                .stream().map(dataset -> toDataset(dataset, listSamples(dataset.getId()))).toList();
    }

    /** 创建同一客户端请求幂等的评价任务，并为每个样本与重复轮次建立槽位。 */
    @Transactional
    public EvaluationTaskDTO createTask(EvaluationTaskCreateDTO request) {
        EvaluationTask existing = findByClientRequest(request.getClientRequestId());
        if (existing != null) {
            requireSameRequest(existing, request);
            return toTask(existing);
        }
        EvaluationDataset dataset = requiredDataset(request.getDatasetId());
        List<EvaluationSample> samples = listSamples(dataset.getId());
        BusinessException.throwIf(samples.isEmpty(), EvaluationErrorCode.DATASET_NOT_FOUND);
        String featureCode = dataset.getFeatureCode() == null ? "REVIEW" : dataset.getFeatureCode();
        EvaluationExperimentRunner runner = runnerRegistry.require(featureCode);
        String modelConfig = request.getModelExecutionConfigVersion() == null
                || request.getModelExecutionConfigVersion().isBlank()
                ? defaultModelConfig(featureCode) : request.getModelExecutionConfigVersion().trim();
        estimateService.requireWithinLimits(new EvaluationEstimateRequestDTO(dataset.getId(),
                List.of(new EvaluationCandidateDTO(request.getWorkflowVersion(), modelConfig,
                        trimToNull(request.getRagIndexVersion()))), request.getRepeatCount()));
        var feature = requireEnabledVersion(runner, request.getWorkflowVersion());
        validateExecutionSelection(featureCode, request);
        BusinessException.throwIf(request.getWeightSchemeId() == null,
                EvaluationErrorCode.WEIGHT_SCHEME_INVALID);
        EvaluationWeightSchemeDTO weightScheme = weightSchemeService.requireActiveForTask(
                request.getWeightSchemeId(), featureCode, EvaluationMetricRegistry.REGISTRY_VERSION);

        LocalDateTime now = LocalDateTime.now();
        EvaluationTask task = new EvaluationTask();
        task.setDatasetId(dataset.getId());
        task.setDatasetVersion(dataset.getDatasetVersion());
        task.setFeatureCode(featureCode);
        task.setWorkflowVersion(request.getWorkflowVersion().trim());
        task.setModelExecutionConfigVersion(modelConfig);
        task.setRagIndexVersion(trimToNull(request.getRagIndexVersion()));
        task.setMetricSetVersion(EvaluationMetricRegistry.REGISTRY_VERSION);
        task.setWeightSchemeId(weightScheme.getSchemeId());
        task.setWeightSchemeVersion(weightScheme.getSchemeVersion());
        try {
            task.setWorkflowSnapshotJson(objectMapper.writeValueAsString(feature));
            task.setMetricDefinitionSnapshotJson(objectMapper.writeValueAsString(
                    metricRegistry.snapshot(featureCode)));
            task.setWeightSchemeSnapshotJson(objectMapper.writeValueAsString(weightScheme));
        } catch (Exception exception) {
            throw new IllegalStateException("评价任务快照序列化失败", exception);
        }
        task.setRepeatCount(request.getRepeatCount());
        task.setClientRequestId(request.getClientRequestId());
        task.setTraceId(currentTraceId());
        task.setStatus("WAITING");
        task.setTotalSlots(samples.size() * request.getRepeatCount());
        task.setTerminalSlots(0);
        task.setFailedSlots(0);
        task.setEnvironmentFailures(0);
        task.setRetryCount(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        List<EvaluationRunAttempt> runs = initialRuns(samples, request.getRepeatCount(), now);
        try {
            persistenceService.createTask(task, runs);
            runs.forEach(run -> enqueueReady(task, run, 0L));
        } catch (DuplicateKeyException exception) {
            EvaluationTask concurrent = findByClientRequest(request.getClientRequestId());
            if (concurrent == null) throw exception;
            requireSameRequest(concurrent, request);
            return toTask(concurrent);
        }
        return toTask(task, runs);
    }

    public EvaluationTaskDTO getTask(Long taskId) {
        return toTask(requiredTask(taskId));
    }

    public long countTasks() {
        return taskMapper.selectCount(null);
    }

    public List<EvaluationTaskSummaryDTO> listRecentTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return taskMapper.selectList(new LambdaQueryWrapper<EvaluationTask>()
                        .orderByDesc(EvaluationTask::getCreateTime).last("LIMIT " + safeLimit))
                .stream().map(this::toSummary).toList();
    }

    /** 相同数据集和重复次数下，每个候选只取最近一次结果；口径不同时禁止排名。 */
    public EvaluationComparisonDTO compare(Long datasetId, Integer repeatCount) {
        EvaluationDataset dataset = requiredDataset(datasetId);
        List<EvaluationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EvaluationTask>()
                .eq(EvaluationTask::getDatasetId, datasetId)
                .eq(EvaluationTask::getRepeatCount, repeatCount)
                .eq(EvaluationTask::getStatus, "COMPLETED")
                .orderByDesc(EvaluationTask::getCreateTime));
        Map<String, EvaluationTask> latest = new LinkedHashMap<>();
        tasks.forEach(task -> latest.putIfAbsent(comparisonCandidateKey(task), task));
        List<EvaluationTask> candidates = List.copyOf(latest.values());
        List<String> incompatibilities = comparisonIncompatibilities(dataset, candidates);
        boolean comparable = candidates.size() > 1 && incompatibilities.isEmpty();
        java.util.stream.Stream<EvaluationTaskSummaryDTO> summaries = candidates.stream().map(this::toSummary);
        if (comparable) {
            summaries = summaries.sorted(Comparator.comparing(EvaluationTaskSummaryDTO::getOverallScore,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        return new EvaluationComparisonDTO(datasetId, dataset.getFeatureCode(),
                dataset.getDatasetVersion(), repeatCount, comparable, comparable,
                List.copyOf(incompatibilities), summaries.toList());
    }

    private String comparisonCandidateKey(EvaluationTask task) {
        return String.join("|", nullToEmpty(task.getWorkflowVersion()),
                nullToEmpty(task.getModelExecutionConfigVersion()), nullToEmpty(task.getRagIndexVersion()));
    }

    private List<String> comparisonIncompatibilities(EvaluationDataset dataset,
                                                       List<EvaluationTask> tasks) {
        List<String> reasons = new ArrayList<>();
        if (tasks.size() < 2) reasons.add("至少需要两个已完成候选");
        if (tasks.stream().anyMatch(task -> !java.util.Objects.equals(dataset.getFeatureCode(),
                task.getFeatureCode()))) reasons.add("featureCode 不一致");
        if (tasks.stream().anyMatch(task -> !java.util.Objects.equals(dataset.getDatasetVersion(),
                task.getDatasetVersion()))) reasons.add("datasetVersion 不一致或缺失");
        if (distinctCount(tasks, EvaluationTask::getMetricSetVersion) > 1
                || tasks.stream().anyMatch(task -> task.getMetricSetVersion() == null)) {
            reasons.add("metricSetVersion 不一致或缺失");
        }
        if (distinctMetricSnapshotCount(tasks) > 1
                || tasks.stream().anyMatch(task -> task.getMetricDefinitionSnapshotJson() == null)) {
            reasons.add("指标定义或参数快照不一致或缺失");
        }
        if (distinctCount(tasks, EvaluationTask::getModelExecutionConfigVersion) > 1
                || tasks.stream().anyMatch(task -> task.getModelExecutionConfigVersion() == null)) {
            reasons.add("modelExecutionConfigVersion 不一致或缺失");
        }
        return reasons;
    }

    private long distinctCount(List<EvaluationTask> tasks,
                               java.util.function.Function<EvaluationTask, String> value) {
        return tasks.stream().map(value).distinct().count();
    }

    private long distinctMetricSnapshotCount(List<EvaluationTask> tasks) {
        return tasks.stream().map(EvaluationTask::getMetricDefinitionSnapshotJson).map(snapshot -> {
            if (snapshot == null) return null;
            try {
                return objectMapper.readTree(snapshot);
            } catch (Exception exception) {
                return objectMapper.getNodeFactory().textNode("INVALID:" + snapshot);
            }
        }).distinct().count();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 由消息唤醒协调器调用；所有槽位结果写入都受当前 fencing token 保护。 */
    public void executeClaimed(Long runId, String leaseToken) {
        EvaluationRunAttempt run = runMapper.selectById(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())
                || !java.util.Objects.equals(leaseToken, run.getLeaseToken())) return;
        EvaluationTask task = taskMapper.selectById(run.getTaskId());
        EvaluationSample sample = sampleMapper.selectById(run.getSampleId());
        if (task == null || sample == null) {
            runMapper.fail(run.getId(), leaseToken, "CONFIGURATION", null, 0L,
                    "评价任务或样本不存在", LocalDateTime.now());
            if (task != null) refreshTask(task);
            return;
        }
        taskMapper.markRunning(task.getId(), LocalDateTime.now());
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId(task.getTraceId())
                .withDomainTask(run.getId().toString(), run.getAttemptNo());
        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            EvaluationExperimentRunner runner = runnerRegistry.require(taskFeature(task));
            EvaluationExperimentCommand command = command(run, sample, task, runner);
            EvaluationExperimentOutcome outcome = runner.parseResult(command, runner.execute(command));
            persistExperimentOutcome(run, leaseToken, runner, outcome);
        } catch (EvaluationRunnerException exception) {
            log.warn("质量评价运行失败 taskId={}, sampleId={}, failureType={}, type={}",
                    task.getId(), sample.getId(), exception.getFailureType(),
                    exception.getClass().getSimpleName());
            runMapper.fail(run.getId(), leaseToken, exception.getFailureType(), null, 0L,
                    exception.getMessage(), LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("质量评价调用失败 taskId={}, sampleId={}, type={}",
                    task.getId(), sample.getId(), exception.getClass().getSimpleName());
            runMapper.fail(run.getId(), leaseToken, "ENVIRONMENT", null, 0L,
                    "实验评审依赖暂不可用", LocalDateTime.now());
        }
        refreshTask(task);
    }

    @Transactional
    public EvaluationTaskDTO retry(Long taskId) {
        EvaluationTask task = requiredTask(taskId);
        BusinessException.throwIf(!"FAILED".equals(task.getStatus()), EvaluationErrorCode.TASK_NOT_FAILED);
        List<EvaluationRunAttempt> latest = latestRuns(taskId);
        List<EvaluationRunAttempt> retries = latest.stream()
                .filter(run -> "FAILED".equals(run.getStatus())
                        && RETRYABLE_FAILURES.contains(run.getFailureType()))
                .map(this::newRetryAttempt).toList();
        BusinessException.throwIf(retries.isEmpty(), EvaluationErrorCode.TASK_NOT_FAILED);
        BusinessException.throwIf(!persistenceService.retry(task, retries), EvaluationErrorCode.TASK_NOT_FAILED);
        retries.forEach(run -> enqueueReady(task, run, 0L));
        int retainedTerminal = latest.size() - retries.size();
        int retainedFailed = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())).count()
                - retries.size();
        taskMapper.updateProgress(task.getId(), "WAITING", retainedTerminal,
                Math.max(0, retainedFailed), 0, null, LocalDateTime.now());
        if (audit != null) audit.emit("EVALUATION.RETRY", "EVALUATION_TASK", String.valueOf(taskId),
                Map.of("retryCount", String.valueOf(retries.size()), "retryReasonCode", "ADMIN_REQUEST"));
        return toTask(requiredTask(taskId));
    }

    public EvaluationTaskDTO pause(Long taskId, Long operatorId) {
        requiredTask(taskId);
        BusinessException.throwIf(taskMapper.pause(taskId, operatorId, LocalDateTime.now()) == 0,
                EvaluationErrorCode.TASK_STATE_CONFLICT);
        if (audit != null) audit.emit("EVALUATION.PAUSE", "EVALUATION_TASK", String.valueOf(taskId),
                Map.of("taskState", "PAUSED", "pauseReasonCode", "ADMIN_REQUEST"));
        return toTask(requiredTask(taskId));
    }

    @Transactional
    public EvaluationTaskDTO resume(Long taskId, Long operatorId) {
        requiredTask(taskId);
        BusinessException.throwIf(taskMapper.resume(taskId, operatorId, LocalDateTime.now()) == 0,
                EvaluationErrorCode.TASK_STATE_CONFLICT);
        EvaluationTask resumed = requiredTask(taskId);
        refreshTask(resumed);
        latestRuns(taskId).stream().filter(run -> "WAITING".equals(run.getStatus()))
                .forEach(run -> enqueueReady(resumed, run, wakeupBucket()));
        if (audit != null) audit.emit("EVALUATION.RESUME", "EVALUATION_TASK", String.valueOf(taskId),
                Map.of("taskState", "WAITING", "resumeReasonCode", "ADMIN_REQUEST"));
        return toTask(requiredTask(taskId));
    }

    public EvaluationTaskDTO cancel(Long taskId, Long operatorId) {
        requiredTask(taskId);
        LocalDateTime now = LocalDateTime.now();
        BusinessException.throwIf(taskMapper.cancel(taskId, operatorId, now) == 0,
                EvaluationErrorCode.TASK_STATE_CONFLICT);
        runMapper.cancelWaiting(taskId, now);
        cancelQueuedGatewayCalls(taskId);
        if (audit != null) audit.emit("EVALUATION.CANCEL", "EVALUATION_TASK", String.valueOf(taskId),
                Map.of("taskState", "CANCELLED", "cancelReasonCode", "ADMIN_REQUEST"));
        return toTask(requiredTask(taskId));
    }

    @Scheduled(fixedDelayString = "${evaluation.recovery.delay-ms:30000}")
    public void recoverStaleRuns() {
        LocalDateTime now = LocalDateTime.now();
        for (EvaluationRunAttempt stale : runMapper.selectExpired(now)) {
            if (persistenceService.recoverExpired(stale, now)) {
                EvaluationTask task = taskMapper.selectById(stale.getTaskId());
                if (task != null) refreshTask(task);
            }
        }
    }

    private void persistExperimentOutcome(EvaluationRunAttempt run, String leaseToken,
                                          EvaluationExperimentRunner runner,
                                          EvaluationExperimentOutcome outcome) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, BigDecimal> metrics = runner.extractMetrics(outcome);
        BigDecimal score = metrics.get("REVIEW_SCORE");
        if ("SUCCEEDED".equals(outcome.status())) {
            String metricsJson;
            try {
                metricsJson = objectMapper.writeValueAsString(metrics);
            } catch (Exception exception) {
                throw new EvaluationRunnerException("OUTPUT", "评价指标无法序列化", exception);
            }
            runMapper.succeed(run.getId(), leaseToken, score, outcome.outputSummaryJson(), metricsJson,
                    outcome.modelName(), outcome.modelExecutionConfigVersion(),
                    outcome.ragIndexVersion(), outcome.aiCallId(), outcome.durationMs(), now);
            return;
        }
        if ("PENDING".equals(outcome.status())) {
            runMapper.deferPending(run.getId(), leaseToken, outcome.durationMs(), outcome.errorMessage(),
                    now.plusSeconds(10), now);
            return;
        }
        if ("UNKNOWN".equals(outcome.status())) {
            runMapper.markUnknown(run.getId(), leaseToken, outcome.aiCallId(), outcome.durationMs(),
                    outcome.errorMessage(), now);
            return;
        }
        runMapper.fail(run.getId(), leaseToken, outcome.failureType(),
                outcome.aiCallId(),
                outcome.durationMs() == null ? 0L : outcome.durationMs(),
                outcome.errorMessage(), now);
    }

    private void refreshTask(EvaluationTask task) {
        EvaluationTask current = taskMapper.selectById(task.getId());
        if (current == null || "PAUSED".equals(current.getStatus())
                || "CANCELLED".equals(current.getStatus())) return;
        task = current;
        List<EvaluationRunAttempt> latest = latestRuns(task.getId());
        int terminal = (int) latest.stream().filter(this::isTerminal).count();
        int failed = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())
                || "UNKNOWN".equals(run.getStatus())).count();
        int unknown = (int) latest.stream().filter(run -> "UNKNOWN".equals(run.getStatus())).count();
        int environment = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())
                && RETRYABLE_FAILURES.contains(run.getFailureType())).count();
        LocalDateTime now = LocalDateTime.now();
        if (terminal < task.getTotalSlots()) {
            taskMapper.updateProgress(task.getId(), "RUNNING", terminal, failed, environment, null, now);
            return;
        }
        if (unknown > 0) {
            taskMapper.fail(task.getId(), terminal, failed, environment,
                    "有 " + unknown + " 个运行的上游结果未知，禁止自动或人工盲目重试", now);
            return;
        }
        if (environment > 0) {
            taskMapper.fail(task.getId(), terminal, failed, environment,
                    "有 " + environment + " 个运行因环境或配置失败，请恢复依赖后重试", now);
            return;
        }
        AiEvaluationCallAggregateDTO callAggregate = callAggregate(task.getId());
        EvaluationMetricsCalculator.Metrics metrics = metricsCalculator.calculate(task, latest, callAggregate);
        String rawMetricsJson;
        try {
            rawMetricsJson = objectMapper.writeValueAsString(metrics.rawMetrics());
        } catch (Exception exception) {
            throw new IllegalStateException("评价原始指标无法序列化", exception);
        }
        EvaluationScoreResultService.ScoreBundle scoreBundle = scoreResultService.calculateInitial(
                task, metrics.rawMetrics(), rawMetricsJson);
        completionPersistenceService.complete(task, terminal, failed, metrics,
                rawMetricsJson, scoreBundle, now);
    }

    private com.leetmodel.common.api.dto.AiFeatureDefinitionDTO requireEnabledVersion(
            EvaluationExperimentRunner runner, String workflowVersion) {
        com.leetmodel.common.api.dto.AiFeatureDefinitionDTO feature;
        try {
            feature = runner.discoverFeature();
        } catch (EvaluationRunnerException exception) {
            log.warn("查询功能版本失败 featureCode={}, type={}",
                    runner.featureCode(), exception.getClass().getSimpleName());
            throw new BusinessException(EvaluationErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        boolean enabled = feature.getWorkflowVersions() != null && feature.getWorkflowVersions().stream()
                .anyMatch(version -> workflowVersion.equals(version.getWorkflowVersion())
                        && "ENABLED".equals(version.getStatus()));
        BusinessException.throwIf(!enabled, EvaluationErrorCode.VERSION_UNAVAILABLE);
        return feature;
    }

    private SubmissionReviewDTO requiredSample(Long submissionId) {
        Result<SubmissionReviewDTO> response;
        try {
            response = submissionFeignClient.getForReview(submissionId);
        } catch (RuntimeException exception) {
            log.warn("查询评价样本失败 submissionId={}, type={}",
                    submissionId, exception.getClass().getSimpleName());
            throw new BusinessException(EvaluationErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        BusinessException.throwIf(response == null, EvaluationErrorCode.DEPENDENCY_UNAVAILABLE);
        BusinessException.throwIf(!response.isSuccess() || response.getData() == null,
                EvaluationErrorCode.SAMPLE_UNAVAILABLE);
        return response.getData();
    }

    private EvaluationDataset requiredDataset(Long id) {
        EvaluationDataset dataset = datasetMapper.selectById(id);
        BusinessException.throwIf(dataset == null, EvaluationErrorCode.DATASET_NOT_FOUND);
        return dataset;
    }

    private EvaluationTask requiredTask(Long id) {
        EvaluationTask task = taskMapper.selectById(id);
        BusinessException.throwIf(task == null, EvaluationErrorCode.TASK_NOT_FOUND);
        return task;
    }

    private EvaluationTask findByClientRequest(String clientRequestId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<EvaluationTask>()
                .eq(EvaluationTask::getClientRequestId, clientRequestId).last("LIMIT 1"));
    }

    private void requireSameRequest(EvaluationTask existing, EvaluationTaskCreateDTO request) {
        boolean same = existing.getDatasetId().equals(request.getDatasetId())
                && existing.getWorkflowVersion().equals(request.getWorkflowVersion().trim())
                && existing.getRepeatCount().equals(request.getRepeatCount())
                && java.util.Objects.equals(existing.getWeightSchemeId(), request.getWeightSchemeId())
                && java.util.Objects.equals(existing.getRagIndexVersion(),
                trimToNull(request.getRagIndexVersion()));
        BusinessException.throwIf(!same, EvaluationErrorCode.IDEMPOTENCY_CONFLICT);
    }

    private List<EvaluationSample> listSamples(Long datasetId) {
        return sampleMapper.selectList(new LambdaQueryWrapper<EvaluationSample>()
                .eq(EvaluationSample::getDatasetId, datasetId)
                .orderByAsc(EvaluationSample::getSortOrder));
    }

    private List<EvaluationRunAttempt> initialRuns(List<EvaluationSample> samples, int repeatCount,
                                                    LocalDateTime now) {
        List<EvaluationRunAttempt> runs = new ArrayList<>();
        for (EvaluationSample sample : samples) {
            for (int repetition = 1; repetition <= repeatCount; repetition++) {
                EvaluationRunAttempt run = new EvaluationRunAttempt();
                run.setSampleId(sample.getId());
                run.setRepetitionNo(repetition);
                run.setAttemptNo(1);
                run.setStatus("WAITING");
                run.setNextRunAt(now);
                run.setRecoveryCount(0);
                run.setCreateTime(now);
                run.setUpdateTime(now);
                runs.add(run);
            }
        }
        return runs;
    }

    private EvaluationExperimentCommand command(EvaluationRunAttempt run, EvaluationSample sample,
                                                EvaluationTask task,
                                                EvaluationExperimentRunner runner) {
        String sampleType = sample.getSampleType() == null
                ? EvaluationSamplePayloadService.REVIEW_SAMPLE_TYPE : sample.getSampleType();
        String schema = sample.getPayloadSchemaVersion() == null
                ? EvaluationSamplePayloadService.REVIEW_SCHEMA : sample.getPayloadSchemaVersion();
        String payload = sample.getPayloadJson();
        if (payload == null && sample.getSubmissionId() != null) {
            try {
                payload = objectMapper.writeValueAsString(Map.of("submissionId", sample.getSubmissionId()));
            } catch (Exception exception) {
                throw new EvaluationRunnerException("CONFIGURATION", "历史评价样本无法转换", exception);
            }
        }
        var validated = runner.validateSample(new EvaluationSamplePayloadDTO(sampleType, schema, payload));
        String experimentRunId = run.getExperimentRunId() == null
                ? taskFeature(task).toLowerCase() + "-eval:" + task.getId() + ":"
                + sample.getId() + ":" + run.getRepetitionNo()
                : run.getExperimentRunId();
        String slotKey = run.getSlotKey() == null
                ? task.getId() + ":" + sample.getId() + ":" + run.getRepetitionNo()
                : run.getSlotKey();
        String idempotencyKey = run.getIdempotencyKey() == null
                ? "evaluation:" + task.getId() + ":" + slotKey + ":attempt:" + run.getAttemptNo()
                : run.getIdempotencyKey();
        return new EvaluationExperimentCommand(experimentRunId, String.valueOf(task.getId()),
                slotKey, run.getAttemptNo(), idempotencyKey, validated, task.getWorkflowVersion(),
                task.getModelExecutionConfigVersion() == null
                        ? defaultModelConfig(taskFeature(task)) : task.getModelExecutionConfigVersion(),
                task.getRagIndexVersion(), "P3");
    }

    private String taskFeature(EvaluationTask task) {
        return task.getFeatureCode() == null ? "REVIEW" : task.getFeatureCode();
    }

    private String defaultModelConfig(String featureCode) {
        if ("REVIEW".equals(featureCode)) return "MODEL_CFG_REVIEW_MULTIMODAL_0001";
        if ("ASSISTANT".equals(featureCode)) return "MODEL_CFG_ASSISTANT_TEXT_0001";
        throw new IllegalArgumentException("功能没有默认模型执行配置: " + featureCode);
    }

    private void validateExecutionSelection(String featureCode, EvaluationTaskCreateDTO request) {
        String rag = trimToNull(request.getRagIndexVersion());
        boolean valid = switch (featureCode) {
            case "REVIEW" -> rag == null;
            case "ASSISTANT" -> "ASSISTANT_RAG_V1".equals(request.getWorkflowVersion())
                    ? rag != null : "ASSISTANT_NO_RAG_V1".equals(request.getWorkflowVersion()) && rag == null;
            default -> false;
        };
        BusinessException.throwIf(!valid, EvaluationErrorCode.VERSION_UNAVAILABLE);
    }

    private List<EvaluationRunAttempt> latestRuns(Long taskId) {
        List<EvaluationRunAttempt> all = runMapper.selectList(
                new LambdaQueryWrapper<EvaluationRunAttempt>()
                        .eq(EvaluationRunAttempt::getTaskId, taskId)
                        .orderByAsc(EvaluationRunAttempt::getAttemptNo));
        Map<RunSlot, EvaluationRunAttempt> latest = new LinkedHashMap<>();
        all.forEach(run -> latest.put(new RunSlot(run.getSampleId(), run.getRepetitionNo()), run));
        return new ArrayList<>(latest.values());
    }

    private EvaluationRunAttempt newRetryAttempt(EvaluationRunAttempt failed) {
        LocalDateTime now = LocalDateTime.now();
        EvaluationRunAttempt retry = new EvaluationRunAttempt();
        retry.setSampleId(failed.getSampleId());
        retry.setRepetitionNo(failed.getRepetitionNo());
        retry.setAttemptNo(failed.getAttemptNo() + 1);
        retry.setStatus("WAITING");
        retry.setNextRunAt(now);
        retry.setRecoveryCount(0);
        retry.setCreateTime(now);
        retry.setUpdateTime(now);
        return retry;
    }

    private boolean isTerminal(EvaluationRunAttempt run) {
        return "SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())
                || "UNKNOWN".equals(run.getStatus()) || "CANCELLED".equals(run.getStatus());
    }

    private EvaluationDatasetDTO toDataset(EvaluationDataset dataset, List<EvaluationSample> samples) {
        return new EvaluationDatasetDTO(dataset.getId(), dataset.getName(), dataset.getDescription(),
                dataset.getStatus(), dataset.getSampleCount(), dataset.getCreatedBy(), dataset.getCreateTime(),
                samples.stream().map(sample -> new EvaluationSampleDTO(
                        sample.getId(), sample.getSubmissionId(), sample.getTeamId(), sample.getProblemId(),
                        sample.getSortOrder(), sample.getNote())).toList(), dataset.getFeatureCode(),
                dataset.getDatasetVersion(), dataset.getSampleSchemaVersion());
    }

    private EvaluationTaskDTO toTask(EvaluationTask task) {
        return toTask(task, latestRuns(task.getId()));
    }

    private EvaluationTaskDTO toTask(EvaluationTask task, List<EvaluationRunAttempt> latestRuns) {
        Map<Long, EvaluationSample> samples = new HashMap<>();
        listSamples(task.getDatasetId()).forEach(sample -> samples.put(sample.getId(), sample));
        EvaluationTaskDTO detail = new EvaluationTaskDTO();
        BeanUtils.copyProperties(toSummary(task), detail);
        detail.setRetryCount(task.getRetryCount());
        detail.setScoreResults(scoreResults(task.getId()));
        if (task.getRawMetricsJson() != null && !task.getRawMetricsJson().isBlank()) {
            try {
                detail.setRawMetrics(objectMapper.readValue(task.getRawMetricsJson(),
                        EvaluationRawMetricsDTO.class));
            } catch (Exception exception) {
                throw new IllegalStateException("评价原始指标快照无法读取", exception);
            }
        }
        detail.setRuns(latestRuns.stream().map(run -> {
            EvaluationSample sample = samples.get(run.getSampleId());
            return new EvaluationRunDTO(run.getId(), run.getSampleId(),
                    sample == null ? null : sample.getSubmissionId(), run.getRepetitionNo(), run.getAttemptNo(),
                    run.getStatus(), run.getFailureType(), run.getScore(), run.getModelName(), run.getAiCallId(),
                    run.getDurationMs(), run.getErrorMessage());
        }).toList());
        return detail;
    }

    private EvaluationTaskSummaryDTO toSummary(EvaluationTask task) {
        List<EvaluationScoreResultDTO> scoreResults = scoreResults(task.getId());
        EvaluationScoreResultDTO latestScore = scoreResults.isEmpty()
                ? null : scoreResults.get(scoreResults.size() - 1);
        return new EvaluationTaskSummaryDTO(task.getId(), task.getDatasetId(), task.getDatasetVersion(),
                task.getFeatureCode(), task.getWorkflowVersion(), task.getModelExecutionConfigVersion(),
                task.getRagIndexVersion(), task.getMetricSetVersion(), task.getWeightSchemeId(),
                task.getWeightSchemeVersion(), latestScore == null ? null : latestScore.getScoreResultVersion(),
                latestScore == null ? null : latestScore.getStatus(),
                latestScore == null ? null : latestScore.getVersionSelectionIndex(), task.getRepeatCount(),
                task.getStatus(), task.getTotalSlots(), task.getTerminalSlots(),
                task.getFailedSlots(), task.getValidityScore(), task.getStabilityScore(), task.getSuccessRate(),
                task.getLatencyScore(), task.getOverallScore(), task.getAvgDurationMs(), task.getErrorMessage(),
                task.getLastOperatedBy(), task.getLastOperation(), task.getLastOperatedAt(),
                task.getCreateTime(), task.getFinishedAt());
    }

    /**
     * 兼容尚无新评分结果的历史任务与测试替身。
     * @param taskId 任务标识
     * @return 评分结果列表
     */
    private List<EvaluationScoreResultDTO> scoreResults(Long taskId) {
        List<EvaluationScoreResultDTO> results = scoreResultService.list(taskId);
        return results == null ? List.of() : results;
    }

    private void cancelQueuedGatewayCalls(Long taskId) {
        AiQueueQueryDTO query = new AiQueueQueryDTO();
        query.setEvaluationTaskId(String.valueOf(taskId));
        query.setLimit(100);
        try {
            Result<List<com.leetmodel.common.api.dto.AiQueueTaskDTO>> response =
                    aiGatewayFeignClient.listQueueTasks(query);
            if (response == null || !response.isSuccess() || response.getData() == null) return;
            response.getData().stream()
                    .filter(item -> Set.of("QUEUED", "LEASED", "RUNNING").contains(item.getState()))
                    .forEach(item -> {
                        try {
                            aiGatewayFeignClient.cancelQueueTask(item.getTaskId());
                        } catch (RuntimeException exception) {
                            log.info("评价任务取消时网关调用已不可取消 taskId={}, queueTaskId={}",
                                    taskId, item.getTaskId());
                        }
                    });
        } catch (RuntimeException exception) {
            log.warn("评价任务已取消，但查询网关排队调用失败 taskId={}", taskId);
        }
    }

    private AiEvaluationCallAggregateDTO callAggregate(Long taskId) {
        try {
            Result<AiEvaluationCallAggregateDTO> response =
                    aiGatewayFeignClient.aggregateEvaluationCalls(String.valueOf(taskId));
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (RuntimeException exception) {
            log.warn("评价任务调用指标暂不可用 taskId={}", taskId);
        }
        return new AiEvaluationCallAggregateDTO();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void enqueueReady(EvaluationTask task, EvaluationRunAttempt run, long bucket) {
        if (readyMessageService != null && task.getId() != null && run.getId() != null) {
            readyMessageService.enqueue(task, run, bucket);
        }
    }

    private String currentTraceId() {
        String traceId = TraceIdUtil.getTraceId();
        return traceId == null || traceId.isBlank() || traceId.length() > 100
                ? UUID.randomUUID().toString() : traceId;
    }

    private long wakeupBucket() {
        return System.currentTimeMillis() / 30_000L;
    }

    private EvaluationSamplePayloadDTO requestPayload(String featureCode,
                                                      EvaluationSampleCreateDTO input) {
        if (input.getPayload() != null) return input.getPayload();
        if (!"REVIEW".equals(featureCode) || input.getSubmissionId() == null) {
            throw new EvaluationRunnerException("CONFIGURATION", "样本必须提供版本化 Payload");
        }
        try {
            return new EvaluationSamplePayloadDTO(EvaluationSamplePayloadService.REVIEW_SAMPLE_TYPE,
                    EvaluationSamplePayloadService.REVIEW_SCHEMA,
                    objectMapper.writeValueAsString(Map.of("submissionId", input.getSubmissionId())));
        } catch (Exception exception) {
            throw new IllegalStateException("评价样本载荷序列化失败", exception);
        }
    }

    private record ResolvedSample(SubmissionReviewDTO submission, ValidatedSamplePayload payload,
                                  String note) {
    }

    private record RunSlot(Long sampleId, Integer repetitionNo) {
    }
}
