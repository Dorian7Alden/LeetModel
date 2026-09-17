package com.leetmodel.evaluation.messaging;

import com.leetmodel.common.api.dto.EvaluationSlotReadyPayload;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import com.leetmodel.evaluation.entity.EvaluationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EvaluationSlotReadyMessageService {
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;

    public void enqueue(EvaluationTask task, EvaluationRunAttempt run, long wakeupBucket) {
        EvaluationSlotReadyPayload payload = new EvaluationSlotReadyPayload(
                task.getId(), run.getId(), run.getSlotKey(), run.getAttemptNo(),
                task.getFeatureCode(), task.getDatasetVersion());
        var envelope = envelopeFactory.create(
                EvaluationSlotMessageContract.EVENT_TYPE,
                "evaluation-slot",
                run.getId().toString(),
                EvaluationSlotMessageContract.idempotencyKey(
                        run.getId(), run.getAttemptNo(), wakeupBucket),
                traceId(task),
                payload);
        try {
            messageOutbox.enqueue(EvaluationSlotMessageContract.TOPIC,
                    EvaluationSlotMessageContract.EVENT_TYPE, envelope);
        } catch (DuplicateKeyException ignored) {
            // 同一物理 attempt 的同一唤醒窗口已经可靠入箱。
        }
    }

    private String traceId(EvaluationTask task) {
        String current = TraceIdUtil.getTraceId();
        if (current != null && !current.isBlank() && current.length() <= 100) return current;
        if (task.getTraceId() != null && !task.getTraceId().isBlank()
                && task.getTraceId().length() <= 100) return task.getTraceId();
        return UUID.randomUUID().toString();
    }
}
