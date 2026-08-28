package com.leetmodel.aigateway.scheduling;

import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiQueueAdmissionServiceTest {

    @Test
    void rejectsFullQueueButKeepsReservedP0Capacity() {
        AiCallTaskMapper mapper = mock(AiCallTaskMapper.class);
        AiSchedulingProperties properties = new AiSchedulingProperties();
        AiQueueAdmissionService service = new AiQueueAdmissionService(mapper, properties);
        AiCallTask p4 = task("P4");
        when(mapper.countActive()).thenReturn(450L);
        when(mapper.countActiveNonP0()).thenReturn(450L);
        assertThat(service.enqueue(p4).errorCode()).isEqualTo("AI_QUEUE_FULL");

        AiCallTask p0 = task("P0");
        assertThat(service.enqueue(p0).created()).isTrue();
    }

    @Test
    void returnsExistingTaskForSameCallerIdempotencyKey() {
        AiCallTaskMapper mapper = mock(AiCallTaskMapper.class);
        AiCallTask existing = task("P0");
        when(mapper.selectByIdempotency(existing.getCallerService(), existing.getIdempotencyKey()))
                .thenReturn(existing);
        AiQueueAdmissionService.AdmissionResult result = new AiQueueAdmissionService(
                mapper, new AiSchedulingProperties()).enqueue(existing);
        assertThat(result.task()).isSameAs(existing);
        assertThat(result.created()).isFalse();
    }

    private AiCallTask task(String priority) {
        AiCallTask task = new AiCallTask();
        task.setCallerService("caller");
        task.setIdempotencyKey("idempotency-" + priority);
        task.setEffectivePriority(priority);
        return task;
    }
}
