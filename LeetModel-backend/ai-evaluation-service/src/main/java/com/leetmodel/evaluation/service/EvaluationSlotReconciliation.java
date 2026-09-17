package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.config.EvaluationWorkerProperties;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationRunAttemptMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import com.leetmodel.evaluation.messaging.EvaluationSlotReadyMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(prefix = "evaluation.worker", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class EvaluationSlotReconciliation {
    private final EvaluationRunAttemptMapper runMapper;
    private final EvaluationTaskMapper taskMapper;
    private final EvaluationSlotReadyMessageService messageService;
    private final EvaluationWorkerProperties properties;
    private final TransactionTemplate transactionTemplate;

    public EvaluationSlotReconciliation(EvaluationRunAttemptMapper runMapper,
                                        EvaluationTaskMapper taskMapper,
                                        EvaluationSlotReadyMessageService messageService,
                                        EvaluationWorkerProperties properties,
                                        TransactionTemplate transactionTemplate) {
        this.runMapper = runMapper;
        this.taskMapper = taskMapper;
        this.messageService = messageService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${evaluation.worker.reconciliation-delay-ms:30000}")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusNanos(properties.getReconciliationDelayMs() * 1_000_000L);
        long bucket = now.toInstant(ZoneOffset.UTC).toEpochMilli()
                / properties.getReconciliationDelayMs();
        for (EvaluationRunAttempt run : runMapper.selectReconciliationCandidates(
                now, before, properties.getReconciliationBatchSize())) {
            EvaluationTask task = taskMapper.selectById(run.getTaskId());
            if (task == null) continue;
            transactionTemplate.executeWithoutResult(status -> {
                if (runMapper.markWakeupEvent(run.getId(), now) == 0) return;
                messageService.enqueue(task, run, bucket);
            });
        }
    }
}
