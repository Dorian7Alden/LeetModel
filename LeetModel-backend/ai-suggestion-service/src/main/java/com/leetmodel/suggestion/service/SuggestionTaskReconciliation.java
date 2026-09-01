package com.leetmodel.suggestion.service;

import com.leetmodel.suggestion.config.SuggestionWorkerProperties;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.messaging.SuggestionReadyMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "suggestion.worker", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SuggestionTaskReconciliation {
    private final SuggestionTaskMapper taskMapper;
    private final SuggestionReadyMessageService messageService;
    private final SuggestionWorkerProperties properties;
    private final TransactionTemplate transactionTemplate;

    public SuggestionTaskReconciliation(SuggestionTaskMapper taskMapper,
                                        SuggestionReadyMessageService messageService,
                                        SuggestionWorkerProperties properties,
                                        TransactionTemplate transactionTemplate) {
        this.taskMapper = taskMapper;
        this.messageService = messageService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /** 低频扫描只修复到期等待或租约过期任务，不直接执行工作流。 */
    @Scheduled(fixedDelayString = "${suggestion.worker.reconciliation-delay-ms:30000}")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusNanos(properties.getReconciliationDelayMs() * 1_000_000L);
        List<SuggestionTask> candidates = taskMapper.selectReconciliationCandidates(
                now, before, properties.getReconciliationBatchSize());
        long bucket = now.toInstant(ZoneOffset.UTC).toEpochMilli()
                / properties.getReconciliationDelayMs();
        for (SuggestionTask candidate : candidates) {
            transactionTemplate.executeWithoutResult(status -> {
                if (taskMapper.markWakeupEvent(candidate.getId(), now) == 0) return;
                messageService.enqueue(candidate, bucket);
            });
        }
    }
}
