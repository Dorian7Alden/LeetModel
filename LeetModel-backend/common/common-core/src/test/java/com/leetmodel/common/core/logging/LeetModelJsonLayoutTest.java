package com.leetmodel.common.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeetModelJsonLayoutTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldWriteVersionedTypedWhitelistSchema() throws Exception {
        LeetModelJsonLayout layout = layout();
        LoggingEvent event = event();
        event.setMDCPropertyMap(Map.of(
                "traceId", "trace-1",
                "operationId", "operation-1",
                "domainTaskId", "task-1",
                "attemptNo", "3",
                "untrustedField", "must-not-appear",
                "service", "forged-service"
        ));
        event.setKeyValuePairs(List.of(
                new KeyValuePair("eventCode", "http_request_completed"),
                new KeyValuePair("httpMethod", "GET"),
                new KeyValuePair("routeTemplate", "/api/problems/{id}"),
                new KeyValuePair("statusCode", 200),
                new KeyValuePair("durationMs", 17L),
                new KeyValuePair("businessType", "problem"),
                new KeyValuePair("messageTopic", "problem-events-v1"),
                new KeyValuePair("retryCount", 2),
                new KeyValuePair("taskState", "SUCCEEDED"),
                new KeyValuePair("outcome", "success")
        ));

        String line = layout.doLayout(event);
        JsonNode value = JSON.readTree(line);

        assertThat(line.lines()).hasSize(1);
        assertThat(value.path("schemaVersion").asText()).isEqualTo("leetmodel.log.v1");
        assertThat(value.path("timestamp").asText()).isEqualTo("2023-11-14T22:13:20.123Z");
        assertThat(value.path("level").asText()).isEqualTo("WARN");
        assertThat(value.path("eventCode").asText()).isEqualTo("HTTP_REQUEST_COMPLETED");
        assertThat(value.path("service").asText()).isEqualTo("test-service");
        assertThat(value.path("environment").asText()).isEqualTo("test");
        assertThat(value.path("serviceVersion").asText()).isEqualTo("1.2.3");
        assertThat(value.path("instance").asText()).isEqualTo("test-instance");
        assertThat(value.path("traceId").asText()).isEqualTo("trace-1");
        assertThat(value.path("operationId").asText()).isEqualTo("operation-1");
        assertThat(value.path("attemptNo").isInt()).isTrue();
        assertThat(value.path("attemptNo").asInt()).isEqualTo(3);
        assertThat(value.path("statusCode").isInt()).isTrue();
        assertThat(value.path("durationMs").isIntegralNumber()).isTrue();
        assertThat(value.path("messageTopic").asText()).isEqualTo("problem-events-v1");
        assertThat(value.path("retryCount").asInt()).isEqualTo(2);
        assertThat(value.path("taskState").asText()).isEqualTo("SUCCEEDED");
        assertThat(value.path("outcome").asText()).isEqualTo("success");
        assertThat(value.has("untrustedField")).isFalse();
    }

    @Test
    void shouldKeepThrowableMessagesOutOfStructuredStack() throws Exception {
        LeetModelJsonLayout layout = layout();
        LoggingEvent event = event();
        event.setMDCPropertyMap(Map.of());
        event.setThrowableProxy(new ThrowableProxy(
                new IllegalStateException("Authorization=Bearer secret-value")));

        JsonNode value = JSON.readTree(layout.doLayout(event));

        assertThat(value.path("exceptionType").asText())
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(value.path("stackTrace").toString()).doesNotContain("secret-value");
        assertThat(value.path("stackTrace").isArray()).isTrue();
    }

    @Test
    void shouldUseStablePlaceholderForMissingOrInvalidEventCode() throws Exception {
        LeetModelJsonLayout layout = layout();
        LoggingEvent missing = event();
        LoggingEvent invalid = event();
        missing.setMDCPropertyMap(Map.of());
        invalid.setMDCPropertyMap(Map.of());
        invalid.setKeyValuePairs(List.of(new KeyValuePair("eventCode", "request id 123")));

        assertThat(JSON.readTree(layout.doLayout(missing)).path("eventCode").asText())
                .isEqualTo("UNCLASSIFIED");
        assertThat(JSON.readTree(layout.doLayout(invalid)).path("eventCode").asText())
                .isEqualTo("UNCLASSIFIED");
    }

    private LeetModelJsonLayout layout() {
        LeetModelJsonLayout layout = new LeetModelJsonLayout();
        layout.setContext(new LoggerContext());
        layout.setService("test-service");
        layout.setEnvironment("test");
        layout.setServiceVersion("1.2.3");
        layout.setInstance("test-instance");
        layout.start();
        return layout;
    }

    private LoggingEvent event() {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(new LoggerContext());
        event.setTimeStamp(1_700_000_000_123L);
        event.setLevel(Level.WARN);
        event.setLoggerName("com.leetmodel.TestLogger");
        event.setThreadName("test-thread");
        event.setMessage("line one\nline two");
        return event;
    }
}
