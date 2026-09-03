package com.leetmodel.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.audit.metrics.AuditMetrics;
import com.leetmodel.audit.messaging.OperationAuditConsumer;
import com.leetmodel.audit.service.AuditArchiveService;
import com.leetmodel.common.api.audit.OperationAuditPayloadV1;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.OperationAuditMessageCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditConsumerTest {
    @Test
    void decodesArchivesAndRestoresCorrelationForValidMessage() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OperationAuditMessageCodec codec = new OperationAuditMessageCodec(mapper, 65536);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMetrics metrics = new AuditMetrics(registry);
        AtomicInteger calls = new AtomicInteger();
        AuditArchiveService archive = new AuditArchiveService(null, mapper, metrics) {
            @Override
            public ArchiveResult archive(MessageEnvelopeV1<OperationAuditPayloadV1> envelope) {
                calls.incrementAndGet();
                return ArchiveResult.CONSUMED;
            }
        };
        OperationAuditConsumer consumer = new OperationAuditConsumer(codec, archive, metrics);

        consumer.onMessage(codec.encode(codec.envelope(payload())));

        assertThat(calls).hasValue(1);
        assertThat(registry.get("audit.archive.events").tag("result", "consumed").counter().count())
                .isZero(); // archive stub does not increment the persistence metric
        assertThat(registry.get("audit.consumer.processing").timer().count()).isEqualTo(1);
    }

    @Test
    void rejectsMalformedMessageAndCountsContractRejection() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OperationAuditMessageCodec codec = new OperationAuditMessageCodec(mapper, 65536);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuditMetrics metrics = new AuditMetrics(registry);
        AuditArchiveService archive = new AuditArchiveService(null, mapper, metrics);
        OperationAuditConsumer consumer = new OperationAuditConsumer(codec, archive, metrics);

        assertThatThrownBy(() -> consumer.onMessage("{}".getBytes()))
                .hasMessageContaining("审计消息契约拒绝");
        assertThat(registry.get("audit.archive.events").tag("result", "rejected").counter().count())
                .isEqualTo(1);
    }

    private OperationAuditPayloadV1 payload() {
        return new OperationAuditPayloadV1(1, "00000000-0000-4000-8000-000000000201",
                "operation-consumer-1", "COMPLETED", Instant.now(), "user-service", "1.0.0",
                "USER_RBAC", "USER.ROLE_CHANGE", "HIGH", "SUCCEEDED", "consumer-test", null,
                "ADMIN", "admin-1", List.of("ROLE_ADMIN"), "USER", "user-1", "v1",
                Map.of("roleCount", "1"), Map.of("roleCount", "2"), "trace-consumer-1",
                null, null, null, null, null, null);
    }
}
