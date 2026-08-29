package com.leetmodel.evaluation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationRunDTO;
import com.leetmodel.common.api.dto.EvaluationSampleCreateDTO;
import com.leetmodel.common.api.dto.EvaluationSampleDTO;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.entity.EvaluationDataset;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationSample;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.enums.EvaluationErrorCode;
import com.leetmodel.evaluation.mapper.EvaluationDatasetMapper;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationSampleMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import com.leetmodel.evaluation.runner.EvaluationExperimentCommand;
import com.leetmodel.evaluation.runner.EvaluationExperimentOutcome;
import com.leetmodel.evaluation.runner.EvaluationExperimentRunner;
import com.leetmodel.evaluation.runner.EvaluationRunnerException;
import com.leetmodel.evaluation.runner.EvaluationRunnerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
@RequiredArgsConstructor
public class EvaluationService {

    private static final Set<String> RETRYABLE_FAILURES = Set.of("ENVIRONMENT", "CONFIGURATION");

    private final EvaluationDatasetMapper datasetMapper;
    private final EvaluationSampleMapper sampleMapper;
    private final EvaluationTaskMapper taskMapper;
    private final EvaluationRunAttemptMapper runMapper;
    private final SubmissionFeignClient submissionFeignClient;
    private final EvaluationRunnerRegistry runnerRegistry;
    private final EvaluationPersistenceService persistenceService;
    private final EvaluationMetricsCalculator metricsCalculator;
    private final ObjectMapper objectMapper;

    @Value("${evaluation.recovery.stale-minutes:15}")
    private long staleMinutes;

    /** 创建后即锁定样本引用，MVP 不提供原地编辑。 */
    public EvaluationDatasetDTO createDataset(EvaluationDatasetCreateDTO request) {
        Set<Long> distinct = request.getSamples().stream()
                .map(EvaluationSampleCreateDTO::getSubmissionId).collect(Collectors.toSet());
        BusinessException.throwIf(distinct.size() != request.getSamples().size(),
                EvaluationErrorCode.DUPLICATE_SAMPLE);

        List<ResolvedSample> resolved = new ArrayList<>();
        for (EvaluationSampleCreateDTO input : request.getSamples()) {
            SubmissionReviewDTO submission = requiredSample(input.getSubmissionId());
            BusinessException.throwIf(!input.getSubmissionId().equals(submission.getId())
                            || submission.getTeamId() == null || submission.getProblemId() == null
                            || submission.getObjectName() == null || submission.getObjectName().isBlank(),
                    EvaluationErrorCode.SAMPLE_UNAVAILABLE);
            resolved.add(new ResolvedSample(submission, trimToNull(input.getNote())));
        }

        LocalDateTime now = LocalDateTime.now();
        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setFeatureCode("REVIEW");
        dataset.setDatasetVersion("REVIEW_DATASET_" + UUID.randomUUID().toString().replace("-", ""));
        dataset.setSampleSchemaVersion(EvaluationSamplePayloadService.REVIEW_SCHEMA);
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
            sample.setSampleType(EvaluationSamplePayloadService.REVIEW_SAMPLE_TYPE);
            sample.setPayloadSchemaVersion(EvaluationSamplePayloadService.REVIEW_SCHEMA);
            try {
                sample.setPayloadJson(objectMapper.writeValueAsString(
                        Map.of("submissionId", item.submission().getId())));
            } catch (Exception exception) {
                throw new IllegalStateException("评价样本载荷序列化失败", exception);
            }
            sample.setSubmissionId(item.submission().getId());
            sample.setTeamId(item.submission().getTeamId());
            sample.setProblemId(item.submission().getProblemId());
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
        var feature = requireEnabledVersion(runner, request.getWorkflowVersion());

        LocalDateTime now = LocalDateTime.now();
        EvaluationTask task = new EvaluationTask();
        task.setDatasetId(dataset.getId());
        task.setFeatureCode(featureCode);
        task.setWorkflowVersion(request.getWorkflowVersion().trim());
        task.setModelExecutionConfigVersion(defaultModelConfig(featureCode));
        task.setMetricSetVersion(EvaluationMetricRegistry.REGISTRY_VERSION);
        try {
            task.setWorkflowSnapshotJson(objectMapper.writeValueAsString(feature));
            task.setMetricDefinitionSnapshotJson(objectMapper.writeValueAsString(
                    Map.of("metricSetVersion", EvaluationMetricRegistry.REGISTRY_VERSION,
                            "featureCode", featureCode)));
        } catch (Exception exception) {
            throw new IllegalStateException("评价任务快照序列化失败", exception);
        }
        task.setRepeatCount(request.getRepeatCount());
        task.setClientRequestId(request.getClientRequestId());
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

    /** 相同数据集和重复次数下，每个版本只取最近一次已完成结果。 */
    public EvaluationComparisonDTO compare(Long datasetId, Integer repeatCount) {
        requiredDataset(datasetId);
        List<EvaluationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EvaluationTask>()
                .eq(EvaluationTask::getDatasetId, datasetId)
                .eq(EvaluationTask::getRepeatCount, repeatCount)
                .eq(EvaluationTask::getStatus, "COMPLETED")
                .orderByDesc(EvaluationTask::getCreateTime));
        Map<String, EvaluationTask> latest = new LinkedHashMap<>();
        tasks.forEach(task -> latest.putIfAbsent(task.getWorkflowVersion(), task));
        List<EvaluationTaskSummaryDTO> versions = latest.values().stream().map(this::toSummary)
                .sorted(Comparator.comparing(EvaluationTaskSummaryDTO::getOverallScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return new EvaluationComparisonDTO(datasetId, repeatCount, versions);
    }

    /** 每次只执行一个样本槽位，避免长事务和一个任务独占工作线程。 */
    @Scheduled(fixedDelayString = "${evaluation.worker.delay-ms:2000}")
    public void processNext() {
        EvaluationRunAttempt run = runMapper.selectNextWaiting();
        if (run == null || runMapper.claim(run.getId(), LocalDateTime.now()) == 0) return;
        EvaluationTask task = taskMapper.selectById(run.getTaskId());
        EvaluationSample sample = sampleMapper.selectById(run.getSampleId());
        if (task == null || sample == null) {
            runMapper.fail(run.getId(), "CONFIGURATION", 0L,
                    "评价任务或样本不存在", LocalDateTime.now());
            if (task != null) refreshTask(task);
            return;
        }
        taskMapper.markRunning(task.getId(), LocalDateTime.now());
        try {
            EvaluationExperimentRunner runner = runnerRegistry.require(taskFeature(task));
            EvaluationExperimentCommand command = command(run, sample, task, runner);
            EvaluationExperimentOutcome outcome = runner.parseResult(command, runner.execute(command));
            persistExperimentOutcome(run, runner, outcome);
        } catch (EvaluationRunnerException exception) {
            log.warn("质量评价运行失败 taskId={}, sampleId={}, failureType={}, message={}",
                    task.getId(), sample.getId(), exception.getFailureType(), exception.getMessage());
            runMapper.fail(run.getId(), exception.getFailureType(), 0L,
                    exception.getMessage(), LocalDateTime.now());
        } catch (Exception exception) {
            log.warn("质量评价调用失败 taskId={}, sampleId={}, message={}",
                    task.getId(), sample.getId(), exception.getMessage());
            runMapper.fail(run.getId(), "ENVIRONMENT", 0L,
                    "实验评审依赖暂不可用", LocalDateTime.now());
        }
        refreshTask(task);
    }

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
        int retainedTerminal = latest.size() - retries.size();
        int retainedFailed = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())).count()
                - retries.size();
        taskMapper.updateProgress(task.getId(), "WAITING", retainedTerminal,
                Math.max(0, retainedFailed), 0, null, LocalDateTime.now());
        return toTask(requiredTask(taskId));
    }

    @Scheduled(fixedDelayString = "${evaluation.recovery.delay-ms:60000}")
    public void recoverStaleRuns() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleMinutes);
        for (EvaluationRunAttempt stale : runMapper.selectStale(cutoff)) {
            if (persistenceService.recoverStale(stale, cutoff)) {
                EvaluationTask task = taskMapper.selectById(stale.getTaskId());
                if (task != null) refreshTask(task);
            }
        }
    }

    private void persistExperimentOutcome(EvaluationRunAttempt run, EvaluationExperimentRunner runner,
                                          EvaluationExperimentOutcome outcome) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, BigDecimal> metrics = runner.extractMetrics(outcome);
        BigDecimal score = metrics.get("REVIEW_SCORE");
        if ("SUCCEEDED".equals(outcome.status())) {
            runMapper.succeed(run.getId(), score, outcome.outputSummaryJson(),
                    outcome.modelName(), outcome.aiCallId(), outcome.durationMs(), now);
            return;
        }
        runMapper.fail(run.getId(), outcome.failureType(),
                outcome.durationMs() == null ? 0L : outcome.durationMs(),
                outcome.errorMessage(), now);
    }

    private void refreshTask(EvaluationTask task) {
        List<EvaluationRunAttempt> latest = latestRuns(task.getId());
        int terminal = (int) latest.stream().filter(this::isTerminal).count();
        int failed = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())).count();
        int environment = (int) latest.stream().filter(run -> "FAILED".equals(run.getStatus())
                && RETRYABLE_FAILURES.contains(run.getFailureType())).count();
        LocalDateTime now = LocalDateTime.now();
        if (terminal < task.getTotalSlots()) {
            taskMapper.updateProgress(task.getId(), "RUNNING", terminal, failed, environment, null, now);
            return;
        }
        if (environment > 0) {
            taskMapper.fail(task.getId(), terminal, failed, environment,
                    "有 " + environment + " 个运行因环境或配置失败，请恢复依赖后重试", now);
            return;
        }
        EvaluationMetricsCalculator.Metrics metrics = metricsCalculator.calculate(task, latest);
        taskMapper.complete(task.getId(), terminal, failed, metrics.validityScore(),
                metrics.stabilityScore(), metrics.successRate(), metrics.latencyScore(),
                metrics.overallScore(), metrics.averageDurationMs(), now);
    }

    private com.leetmodel.common.api.dto.AiFeatureDefinitionDTO requireEnabledVersion(
            EvaluationExperimentRunner runner, String workflowVersion) {
        com.leetmodel.common.api.dto.AiFeatureDefinitionDTO feature;
        try {
            feature = runner.discoverFeature();
        } catch (EvaluationRunnerException exception) {
            log.warn("查询功能版本失败 featureCode={}, message={}", runner.featureCode(), exception.getMessage());
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
            log.warn("查询评价样本失败 submissionId={}, message={}", submissionId, exception.getMessage());
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
                && existing.getRepeatCount().equals(request.getRepeatCount());
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
        return new EvaluationExperimentCommand(experimentRunId, validated, task.getWorkflowVersion(),
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
        retry.setCreateTime(now);
        retry.setUpdateTime(now);
        return retry;
    }

    private boolean isTerminal(EvaluationRunAttempt run) {
        return "SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus());
    }

    private EvaluationDatasetDTO toDataset(EvaluationDataset dataset, List<EvaluationSample> samples) {
        return new EvaluationDatasetDTO(dataset.getId(), dataset.getName(), dataset.getDescription(),
                dataset.getStatus(), dataset.getSampleCount(), dataset.getCreatedBy(), dataset.getCreateTime(),
                samples.stream().map(sample -> new EvaluationSampleDTO(
                        sample.getId(), sample.getSubmissionId(), sample.getTeamId(), sample.getProblemId(),
                        sample.getSortOrder(), sample.getNote())).toList());
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
        return new EvaluationTaskSummaryDTO(task.getId(), task.getDatasetId(), task.getWorkflowVersion(),
                task.getRepeatCount(), task.getStatus(), task.getTotalSlots(), task.getTerminalSlots(),
                task.getFailedSlots(), task.getValidityScore(), task.getStabilityScore(), task.getSuccessRate(),
                task.getLatencyScore(), task.getOverallScore(), task.getAvgDurationMs(), task.getErrorMessage(),
                task.getCreateTime(), task.getFinishedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ResolvedSample(SubmissionReviewDTO submission, String note) {
    }

    private record RunSlot(Long sampleId, Integer repetitionNo) {
    }
}
