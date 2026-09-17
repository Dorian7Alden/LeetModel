package com.leetmodel.common.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SkyWalkingLogReporterAppenderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<SkyWalkingLogReporterAppender> appenders = new CopyOnWriteArrayList<>();
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();

    @BeforeEach
    void resetMetrics() {
        SkyWalkingLogReporterMetrics.resetForTest();
    }

    @AfterEach
    void cleanup() {
        appenders.forEach(SkyWalkingLogReporterAppender::stop);
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void shouldBatchNativeHttpRecordsAndExposeSuccessMetrics() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = server(exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        SkyWalkingLogReporterAppender appender = appender(server, 8, 4, 50L, 1);

        appender.doAppend(event(Level.INFO, "safe message", "trace-1"));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(requests).hasSize(1);
            assertThat(SkyWalkingLogReporterMetrics.snapshot().succeeded()).isEqualTo(1L);
        });
        JsonNode record = JSON.readTree(requests.get(0)).get(0);
        JsonNode body = JSON.readTree(record.path("body").path("json").path("json").asText());
        assertThat(record.path("service").asText()).isEqualTo("reporter-test");
        assertThat(record.path("serviceInstance").asText()).isEqualTo("test-instance");
        assertThat(record.path("endpoint").asText()).isEqualTo("/api/problems/{id}");
        assertThat(body.path("schemaVersion").asText()).isEqualTo("leetmodel.log.v1");
        assertThat(body.path("traceId").asText()).isEqualTo("trace-1");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SkyWalkingLogReporterMetrics.meterBinder().bindTo(registry);
        assertThat(registry.get(SkyWalkingLogReporterMetrics.EVENTS_METRIC)
                .tags("outcome", "succeeded", "cause", "none")
                .functionCounter().count()).isEqualTo(1d);
        assertThat(registry.get(SkyWalkingLogReporterMetrics.CONNECTED_METRIC)
                .gauge().value()).isEqualTo(1d);
        assertThat(SkyWalkingLogReporterMetrics.snapshot().recovered()).isZero();
    }

    @Test
    void shouldCountRecoveryOnlyAfterARealTransportFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(exchange -> {
            int response = calls.incrementAndGet() == 1 ? 503 : 200;
            exchange.sendResponseHeaders(response, -1);
            exchange.close();
        });
        SkyWalkingLogReporterAppender appender = appender(server, 8, 1, 10L, 1);

        appender.doAppend(event(Level.INFO, "first", "trace-failure"));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(SkyWalkingLogReporterMetrics.snapshot().failed()).isEqualTo(1L);
            assertThat(SkyWalkingLogReporterMetrics.snapshot().connected()).isZero();
        });
        appender.doAppend(event(Level.INFO, "second", "trace-recovery"));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(SkyWalkingLogReporterMetrics.snapshot().succeeded()).isEqualTo(1L);
            assertThat(SkyWalkingLogReporterMetrics.snapshot().recovered()).isEqualTo(1L);
            assertThat(SkyWalkingLogReporterMetrics.snapshot().connected()).isEqualTo(1);
        });
    }

    @Test
    void shouldNeverBlockCallerAndBoundQueueWhenCollectorIsSlow() throws Exception {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(800L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        SkyWalkingLogReporterAppender appender = appender(server, 2, 1, 10L, 1);
        appender.setRequestTimeoutMillis(150L);

        long started = System.nanoTime();
        for (int index = 0; index < 200; index++) {
            appender.doAppend(event(Level.INFO, "burst " + index, "trace-burst"));
        }
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(elapsedMillis).isLessThan(500L);
        assertThat(appender.queueDepth()).isLessThanOrEqualTo(2);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(SkyWalkingLogReporterMetrics.snapshot().droppedQueueLow())
                        .isGreaterThan(0L));
    }

    @Test
    void shouldEvictLowPriorityBeforeDroppingError() throws Exception {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(600L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        SkyWalkingLogReporterAppender appender = appender(server, 1, 1, 10L, 1);
        appender.setRequestTimeoutMillis(150L);

        appender.doAppend(event(Level.INFO, "first", "trace-1"));
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(appender.queueDepth()).isZero());
        appender.doAppend(event(Level.INFO, "queued-low", "trace-2"));
        appender.doAppend(event(Level.ERROR, "important-error", "trace-3"));

        SkyWalkingLogReporterMetrics.Snapshot snapshot = SkyWalkingLogReporterMetrics.snapshot();
        assertThat(snapshot.droppedQueueLow()).isEqualTo(1L);
        assertThat(snapshot.droppedQueueHigh()).isZero();
        assertThat(appender.queueDepth()).isEqualTo(1);
    }

    private SkyWalkingLogReporterAppender appender(HttpServer server, int capacity,
                                                    int batchSize, long flushMillis,
                                                    int maxAttempts) {
        LoggerContext context = new LoggerContext();
        LeetModelJsonLayout layout = new LeetModelJsonLayout();
        layout.setContext(context);
        layout.setService("reporter-test");
        layout.setEnvironment("test");
        layout.setServiceVersion("1.0.0");
        layout.setInstance("test-instance");
        layout.start();

        SkyWalkingLogReporterAppender appender = new SkyWalkingLogReporterAppender();
        appender.setContext(context);
        appender.setEnabled(true);
        appender.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/logs");
        appender.setService("reporter-test");
        appender.setInstance("test-instance");
        appender.setQueueCapacity(capacity);
        appender.setBatchSize(batchSize);
        appender.setFlushIntervalMillis(flushMillis);
        appender.setConnectTimeoutMillis(100L);
        appender.setRequestTimeoutMillis(300L);
        appender.setMaxAttempts(maxAttempts);
        appender.setRetryBackoffMillis(10L);
        appender.setLayout(layout);
        appender.start();
        appenders.add(appender);
        return appender;
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/logs", handler);
        server.start();
        servers.add(server);
        return server;
    }

    private LoggingEvent event(Level level, String message, String traceId) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(new LoggerContext());
        event.setLoggerName("com.leetmodel.test.Reporter");
        event.setThreadName("test-thread");
        event.setTimeStamp(System.currentTimeMillis());
        event.setLevel(level);
        event.setMessage(message);
        event.setMDCPropertyMap(java.util.Map.of("traceId", traceId));
        event.setKeyValuePairs(List.of(
                new KeyValuePair(LogFieldNames.EVENT_CODE, "HTTP_REQUEST_COMPLETED"),
                new KeyValuePair(LogFieldNames.ROUTE_TEMPLATE, "/api/problems/{id}")
        ));
        return event;
    }
}
