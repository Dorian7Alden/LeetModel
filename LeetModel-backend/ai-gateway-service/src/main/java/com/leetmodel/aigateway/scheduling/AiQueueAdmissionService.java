package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.observability.AiGatewayMetrics;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 队列准入与调用方幂等入口。 */
@Service
public class AiQueueAdmissionService {

    private final AiCallTaskMapper mapper;
    private final AiSchedulingProperties properties;
    private final AiGatewayMetrics metrics;

    public AiQueueAdmissionService(AiCallTaskMapper mapper, AiSchedulingProperties properties,
                                   AiGatewayMetrics metrics) {
        this.mapper = mapper;
        this.properties = properties;
        this.metrics = metrics;
    }

    public AdmissionResult enqueue(AiCallTask task) {
        AiCallTask existing = mapper.selectByIdempotency(task.getCallerService(), task.getIdempotencyKey());
        if (existing != null) {
            metrics.admission(existing.getEffectivePriority(), "idempotent");
            return new AdmissionResult(existing, false, null);
        }
        long active = mapper.countActive();
        if (active >= properties.getMaxQueueSize()) {
            metrics.admission(task.getEffectivePriority(), "rejected");
            return new AdmissionResult(null, false, "AI_QUEUE_FULL");
        }
        if (!"P0".equals(task.getEffectivePriority())
                && mapper.countActiveNonP0() >= properties.getMaxQueueSize() - properties.getReservedP0QueueSize()) {
            metrics.admission(task.getEffectivePriority(), "rejected");
            return new AdmissionResult(null, false, "AI_QUEUE_FULL");
        }
        try {
            mapper.insert(task);
            metrics.admission(task.getEffectivePriority(), "accepted");
            return new AdmissionResult(task, true, null);
        } catch (DuplicateKeyException exception) {
            existing = mapper.selectByIdempotency(task.getCallerService(), task.getIdempotencyKey());
            if (existing != null) {
                metrics.admission(existing.getEffectivePriority(), "idempotent");
                return new AdmissionResult(existing, false, null);
            }
            throw exception;
        }
    }

    public record AdmissionResult(AiCallTask task, boolean created, String errorCode) {}
}
