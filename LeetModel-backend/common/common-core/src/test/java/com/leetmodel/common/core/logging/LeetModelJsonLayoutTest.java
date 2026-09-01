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
                new KeyValuePair("suppressedCount", 5L),
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
        assertThat(value.path("suppressedCount").asLong()).isEqualTo(5L);
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

    @Test
    void shouldRedactCredentialsContentAndThirdPartyQueriesAtFinalBoundary() throws Exception {
        LeetModelJsonLayout layout = layout();
        LoggingEvent event = event("Authorization: Bearer relay-secret-123 Cookie=session-secret "
                + "password=db-secret verificationCode=otp-secret JWT=jwt-secret "
                + "accessKey=minio-access secretKey=minio-secret apiKey=model-secret "
                + "relayToken=relay-token-secret prompt=paper-private-content "
                + "answer=answer-private ragContext=rag-private embedding=vector-private "
                + "payload=message-private "
                + "url=https://provider.example/v1/chat?api_key=query-secret&user=42 "
                + "jdbc:mysql://db.example/lm?user=app&password=db-query-secret "
                + "Using generated security password: generated-secret");
        event.setMDCPropertyMap(Map.of(
                "traceId", "trace-ok\r\nforged-line",
                "operationId", "operation-password=mdc-secret"
        ));

        JsonNode value = JSON.readTree(layout.doLayout(event));
        String encoded = value.toString();

        assertThat(encoded).doesNotContain(
                "relay-secret-123", "session-secret", "db-secret", "otp-secret", "jwt-secret",
                "minio-access", "minio-secret", "model-secret", "relay-token-secret",
                "paper-private-content", "answer-private", "rag-private", "vector-private",
                "message-private", "query-secret", "db-query-secret", "generated-secret",
                "forged-line\n");
        assertThat(value.path("message").asText()).contains(LogSanitizer.REDACTED);
        assertThat(value.path("traceId").asText()).isEqualTo("trace-ok forged-line");
        assertThat(value.path("operationId").asText()).doesNotContain("mdc-secret");
    }

    @Test
    void shouldRemoveRecordSeparatorsAndBoundEveryString() throws Exception {
        LeetModelJsonLayout layout = layout();
        LoggingEvent event = event("start\r\n\u0000\u001bend " + "x".repeat(2_000));
        event.setLoggerName("l".repeat(500));
        event.setThreadName("t".repeat(500));
        event.setMDCPropertyMap(Map.of("traceId", "i".repeat(500)));
        event.setKeyValuePairs(List.of(new KeyValuePair("routeTemplate", "r".repeat(500))));

        String line = layout.doLayout(event);
        JsonNode value = JSON.readTree(line);

        assertThat(line.lines()).hasSize(1);
        assertThat(value.path("message").asText())
                .doesNotContain("\r", "\n", "\u0000", "\u001b")
                .hasSizeLessThanOrEqualTo(LogSanitizer.MAX_MESSAGE_LENGTH)
                .endsWith(LogSanitizer.TRUNCATED);
        assertThat(value.path("logger").asText()).hasSizeLessThanOrEqualTo(LogSanitizer.MAX_FIELD_LENGTH);
        assertThat(value.path("thread").asText()).hasSizeLessThanOrEqualTo(LogSanitizer.MAX_FIELD_LENGTH);
        assertThat(value.path("routeTemplate").asText()).hasSizeLessThanOrEqualTo(LogSanitizer.MAX_FIELD_LENGTH);
        assertThat(value.path("traceId").asText()).hasSizeLessThanOrEqualTo(LogSanitizer.MAX_IDENTIFIER_LENGTH);
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
        return event("line one\nline two");
    }

    private LoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(new LoggerContext());
        event.setTimeStamp(1_700_000_000_123L);
        event.setLevel(Level.WARN);
        event.setMessage(message);
        return event;
    }
}
