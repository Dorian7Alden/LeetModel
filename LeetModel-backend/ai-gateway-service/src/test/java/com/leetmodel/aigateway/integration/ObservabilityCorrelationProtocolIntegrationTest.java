package com.leetmodel.aigateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.observability.AiGatewayMetrics;
import com.leetmodel.aigateway.scheduling.AiQueueRecoveryService;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.ExecutionSpanOperation;
import com.leetmodel.common.core.telemetry.SkyWalkingExecutionSpan;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TRACE-03 的真实协议夹具：只在显式门禁中生成脱敏的 H2 权威事实。
 *
 * <p>本类不会保存消息正文、Prompt、回答、Token 或原始异常；产物只包含联合定位所需的
 * 固定状态与关联标识。普通单元测试不会运行本夹具。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:ai-call-log-test-schema.sql,classpath:observability-correlation-test-schema.sql",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "ai.cost-enrichment.poll-delay-ms=3600000",
        "ai.scheduling.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RUN_OBSERVABILITY_CORRELATION_INTEGRATION", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ObservabilityCorrelationProtocolIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            ObservabilityCorrelationProtocolIntegrationTest.class);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private AiCallTaskMapper taskMapper;

    @Autowired
    private AiCallAttemptMapper attemptMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String database = safeRunId();
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:correlation_" + database
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("ai.new-api.relay-token", () -> "test-token");
    }

    @Test
    @Order(1)
    void outboxBacklogKeepsTraceablePersistedFact() throws Exception {
        String runId = safeRunId();
        String traceId = "trace-correlation-outbox-" + runId;
        String eventId = UUID.nameUUIDFromBytes(("outbox-" + runId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        LocalDateTime now = utcNow();
        jdbcTemplate.update("""
                INSERT INTO message_outbox
                  (event_id, topic, tag, event_type, trace_id, status, retry_count,
                   last_error, occurred_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 1, ?, ?, ?, ?)
                """, eventId, "lm-dev%review-task-v1", "REVIEW_TASK_READY",
                "REVIEW_TASK_READY", traceId, "TRANSPORT_UNAVAILABLE",
                now.minusMinutes(2), now.minusMinutes(2), now);

        CorrelationSnapshot correlation = CorrelationSnapshot.EMPTY
                .withTraceId(traceId).withMessage(eventId, null);
        try (SkyWalkingExecutionSpan span = SkyWalkingExecutionSpan.open(
                ExecutionSpanOperation.OUTBOX_PUBLISH, correlation).attemptKind(false)) {
            span.outcome("retry").error("transport");
            LOG.atWarn()
                    .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.OUTBOX_PUBLISH_RETRY)
                    .addKeyValue(LogFieldNames.EVENT_ID, eventId)
                    .addKeyValue(LogFieldNames.MESSAGE_TOPIC, "lm-dev%review-task-v1")
                    .addKeyValue(LogFieldNames.RETRY_COUNT, 1)
                    .addKeyValue(LogFieldNames.ERROR_CODE, "TRANSPORT_UNAVAILABLE")
                    .addKeyValue(LogFieldNames.OUTCOME, "retry")
                    .log("Outbox publish scheduled for retry");
        }

        Map<String, Object> fact = jdbcTemplate.queryForObject("""
                SELECT event_id, trace_id, topic, status, retry_count, last_error
                FROM message_outbox WHERE event_id = ?
                """, (resultSet, rowNumber) -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("eventId", resultSet.getString("event_id"));
            value.put("traceId", resultSet.getString("trace_id"));
            value.put("topic", resultSet.getString("topic"));
            value.put("status", resultSet.getString("status"));
            value.put("retryCount", resultSet.getInt("retry_count"));
            value.put("errorCode", resultSet.getString("last_error"));
            return value;
        }, eventId);
        assertThat(fact).containsEntry("status", "PENDING");
        writeSnapshot("OBSERVABILITY_OUTBOX_FACT_OUTPUT", "outbox_backlog", traceId,
                "Messaging/OutboxPublishAttempt", "OUTBOX_PUBLISH_RETRY", fact);
    }

    @Test
    @Order(2)
    void aiUnknownKeepsTaskAttemptAndCallFacts() throws Exception {
        String runId = safeRunId();
        String suffix = runId.length() > 24 ? runId.substring(0, 24) : runId;
        String taskId = "task-correlation-" + suffix;
        String traceId = "trace-correlation-ai-" + runId;
        LocalDateTime now = utcNow();
        AiCallTask task = task(taskId, traceId, now);
        taskMapper.insert(task);
        assertThat(taskMapper.claimQueued(taskId, 0, "dead-owner", now.minusSeconds(1), now))
                .isEqualTo(1);
        AiCallTask leased = taskMapper.selectByTaskId(taskId);
        AiCallAttempt attempt = attempt(leased, "attempt-correlation-" + suffix, now);
        attemptMapper.insert(attempt);
        assertThat(taskMapper.transition(taskId, leased.getVersion(), "LEASED", "RUNNING",
                leased.getLeaseOwner(), now)).isEqualTo(1);

        AiQueueRecoveryService recovery = new AiQueueRecoveryService(taskMapper, attemptMapper,
                new AiTaskWaitRegistry(), mock(AiGatewayMetrics.class));
        assertThat(recovery.recoverOnce()).isEqualTo(1);

        AiCallTask terminal = taskMapper.selectByTaskId(taskId);
        AiCallAttempt terminalAttempt = attemptMapper.selectLatest(taskId);
        assertThat(terminal.getState()).isEqualTo("FAILED");
        assertThat(terminal.getErrorCode()).isEqualTo("AI_UPSTREAM_RESULT_UNKNOWN");
        assertThat(terminalAttempt.getState()).isEqualTo("UNKNOWN");

        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("domainTaskId", terminal.getTaskId());
        fact.put("traceId", terminal.getTraceId());
        fact.put("attemptNo", terminalAttempt.getAttemptNo());
        fact.put("aiCallId", terminal.getCallId());
        fact.put("taskState", terminal.getState());
        fact.put("attemptState", terminalAttempt.getState());
        fact.put("errorCode", terminal.getErrorCode());
        fact.put("deadLetterReason", terminal.getDeadLetterReason());
        writeSnapshot("OBSERVABILITY_AI_FACT_OUTPUT", "ai_unknown", traceId,
                "AI/RecoveryAttempt", "AI_CALL_RESULT_UNKNOWN", fact);
    }

    private static AiCallTask task(String taskId, String traceId, LocalDateTime now) {
        AiCallTask task = new AiCallTask();
        task.setTaskId(taskId);
        task.setCallId("call-" + taskId);
        task.setTraceId(traceId);
        task.setCallerService("ai-assistant-service");
        task.setIdempotencyKey("idem-" + taskId);
        task.setCallType("CHAT");
        task.setFeatureCode("AI_ASSISTANT");
        task.setOperationCode("CHAT_REPLY");
        task.setDeclaredPriority("P0");
        task.setEffectivePriority("P0");
        task.setState("QUEUED");
        task.setRequestHash("a".repeat(64));
        task.setRequestPayload("{}");
        task.setDeadline(now.plusMinutes(2));
        task.setMaxQueueWaitMs(10_000L);
        task.setAttemptCount(0);
        task.setVersion(0L);
        task.setCancelRequested(false);
        task.setQueuedAt(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setDeleted(0);
        return task;
    }

    private static AiCallAttempt attempt(AiCallTask task, String attemptId, LocalDateTime now) {
        AiCallAttempt attempt = new AiCallAttempt();
        attempt.setAttemptId(attemptId);
        attempt.setTaskId(task.getTaskId());
        attempt.setAttemptNo(1);
        attempt.setState("DISPATCHING");
        attempt.setOwner("dead-owner");
        attempt.setPreparedAt(now.minusSeconds(2));
        attempt.setSentAt(now.minusSeconds(1));
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        attempt.setDeleted(0);
        return attempt;
    }

    private static void writeSnapshot(String environmentName, String scenario, String traceId,
                                      String operation, String eventCode, Map<String, Object> fact)
            throws Exception {
        String destination = System.getenv(environmentName);
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException(environmentName + " is required for the protocol drill");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", "leetmodel.correlation.fact.v1");
        snapshot.put("scenario", scenario);
        snapshot.put("service", System.getProperty("spring.application.name", "ai-gateway-service"));
        snapshot.put("businessTraceId", traceId);
        snapshot.put("traceOperation", operation);
        snapshot.put("logEventCode", eventCode);
        snapshot.put("fact", fact);
        Path path = Path.of(destination).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), snapshot);
    }

    private static String safeRunId() {
        String value = System.getenv().getOrDefault("OBSERVABILITY_CORRELATION_RUN_ID", "local");
        String normalized = value.replaceAll("[^A-Za-z0-9_-]", "-");
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
