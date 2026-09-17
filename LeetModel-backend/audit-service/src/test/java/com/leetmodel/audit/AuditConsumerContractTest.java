package com.leetmodel.audit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AuditConsumerContractTest {
    @Test
    void shouldUseDedicatedTopicAndBoundedRetry() throws IOException {
        String source = read("src/main/java/com/leetmodel/audit/messaging/OperationAuditConsumer.java");
        assertThat(source).contains("leetmodel-operation-audit-v1", "cg-audit-archive-v1",
                "maxReconsumeTimes = OperationAuditResources.MAX_RECONSUME_TIMES",
                "MessageCorrelationContext.open(envelope)");
        assertThat(source).contains("throw new MessageContractException");
    }

    @Test
    void metricsMustNotPromoteIdentifiersToLabels() throws IOException {
        String source = read("src/main/java/com/leetmodel/audit/metrics/AuditMetrics.java");
        assertThat(source).doesNotContain("operationId", "eventId", "traceId", "userId", "taskId");
        assertThat(source).contains("audit.archive.events", "audit.consumer.dlq",
                "audit.integrity.incomplete.active");
    }

    private String read(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java",
                path.substring(path.indexOf("com/"))), StandardCharsets.UTF_8);
    }
}
