package com.leetmodel.suggestion.messaging;

import com.leetmodel.common.api.dto.SuggestionTaskReadyPayload;
import com.leetmodel.common.core.util.TraceIdUtil;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessageOutbox;
import com.leetmodel.suggestion.entity.SuggestionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SuggestionReadyMessageService {
    private final MessageEnvelopeFactory envelopeFactory;
    private final MessageOutbox messageOutbox;

    public void enqueue(SuggestionTask task, long wakeupBucket) {
        int attempt = task.getAttemptNo() == null ? 1 : task.getAttemptNo();
        SuggestionTaskReadyPayload payload = new SuggestionTaskReadyPayload(
                task.getId(), task.getSubmissionId(), task.getWorkflowVersion());
        var envelope = envelopeFactory.create(
                SuggestionTaskMessageContract.EVENT_TYPE,
                "suggestion-task",
                task.getId().toString(),
                SuggestionTaskMessageContract.idempotencyKey(task.getId(), attempt, wakeupBucket),
                traceId(task),
                payload);
        try {
            messageOutbox.enqueue(SuggestionTaskMessageContract.TOPIC,
                    SuggestionTaskMessageContract.EVENT_TYPE, envelope);
        } catch (DuplicateKeyException ignored) {
            // 同一任务 attempt 的同一唤醒窗口已经可靠入箱。
        }
    }

    private String traceId(SuggestionTask task) {
        String current = TraceIdUtil.getTraceId();
        if (current != null && !current.isBlank() && current.length() <= 100) return current;
        if (task.getTraceId() != null && !task.getTraceId().isBlank()
                && task.getTraceId().length() <= 100) return task.getTraceId();
        return UUID.randomUUID().toString();
    }
}
