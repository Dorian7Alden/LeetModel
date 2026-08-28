package com.leetmodel.aigateway.integration;

import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.entity.AiCallAttempt;
import com.leetmodel.aigateway.mapper.AiCallAttemptMapper;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.scheduling.AiQueueRecoveryService;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=", "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false", "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:ai-call-log-test-schema.sql",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "ai.cost-enrichment.poll-delay-ms=3600000", "ai.scheduling.enabled=false"
})
class AiCallTaskRepositoryIntegrationTest {

    @Autowired
    private AiCallTaskMapper mapper;

    @Autowired
    private AiCallAttemptMapper attemptMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:ai_queue_repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("ai.new-api.relay-token", () -> "test-token");
    }

    @Test
    void concurrentClaimHasOneWinnerAndTransitionsAreConditional() throws Exception {
        AiCallTask task = task("task-concurrent", "idem-concurrent");
        mapper.insert(task);
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> claims = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                String owner = "worker-" + index;
                claims.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    LocalDateTime now = utcNow();
                    return mapper.claimQueued(task.getTaskId(), 0, owner, now.plusSeconds(15), now);
                }));
            }
            ready.await();
            start.countDown();
            int winners = 0;
            for (Future<Integer> claim : claims) winners += claim.get();
            assertThat(winners).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        AiCallTask leased = mapper.selectById(task.getId());
        assertThat(leased.getState()).isEqualTo("LEASED");
        assertThat(leased.getVersion()).isEqualTo(1);
        assertThat(mapper.transition(leased.getTaskId(), leased.getVersion(), "LEASED", "RUNNING",
                leased.getLeaseOwner(), utcNow())).isEqualTo(1);
        assertThat(mapper.transition(leased.getTaskId(), leased.getVersion(), "LEASED", "RUNNING",
                leased.getLeaseOwner(), utcNow())).isZero();
    }

    @Test
    void callerIdempotencyIsQueryable() {
        AiCallTask task = task("task-idempotent", "idem-idempotent");
        mapper.insert(task);
        assertThat(mapper.selectByIdempotency("ai-assistant-service", "idem-idempotent").getTaskId())
                .isEqualTo("task-idempotent");
    }

    @Test
    void expiredPreparedAttemptIsSafelyRequeuedAfterRestart() {
        AiCallTask task = task("task-prepared-recovery", "idem-prepared-recovery");
        mapper.insert(task);
        LocalDateTime now = utcNow();
        assertThat(mapper.claimQueued(task.getTaskId(), 0, "dead-owner", now.minusSeconds(1), now))
                .isEqualTo(1);
        AiCallTask leased = mapper.selectByTaskId(task.getTaskId());
        AiCallAttempt attempt = attempt(leased, "attempt-prepared", "PREPARED");
        attemptMapper.insert(attempt);

        assertThat(recovery().recoverOnce()).isEqualTo(1);

        assertThat(mapper.selectByTaskId(task.getTaskId()).getState()).isEqualTo("QUEUED");
        assertThat(attemptMapper.selectLatest(task.getTaskId()).getState()).isEqualTo("FAILED");
    }

    @Test
    void expiredDispatchedAttemptBecomesUnknownAndIsNeverRequeued() {
        AiCallTask task = task("task-unknown-recovery", "idem-unknown-recovery");
        mapper.insert(task);
        LocalDateTime now = utcNow();
        assertThat(mapper.claimQueued(task.getTaskId(), 0, "dead-owner", now.minusSeconds(1), now))
                .isEqualTo(1);
        AiCallTask leased = mapper.selectByTaskId(task.getTaskId());
        AiCallAttempt attempt = attempt(leased, "attempt-unknown", "DISPATCHING");
        attemptMapper.insert(attempt);
        assertThat(mapper.transition(task.getTaskId(), leased.getVersion(), "LEASED", "RUNNING",
                leased.getLeaseOwner(), now)).isEqualTo(1);

        assertThat(recovery().recoverOnce()).isEqualTo(1);
        AiCallTask failed = mapper.selectByTaskId(task.getTaskId());
        assertThat(failed.getState()).isEqualTo("FAILED");
        assertThat(failed.getErrorCode()).isEqualTo("AI_UPSTREAM_RESULT_UNKNOWN");
        assertThat(failed.getDeadLetterReason()).isEqualTo("LEASE_EXPIRED_AFTER_DISPATCH");
        assertThat(attemptMapper.selectLatest(task.getTaskId()).getState()).isEqualTo("UNKNOWN");
        assertThat(recovery().recoverOnce()).isZero();
    }

    @Test
    void cancellationStopsQueuedWorkButOnlyMarksRunningWork() {
        AiCallTask queued = task("task-cancel-queued", "idem-cancel-queued");
        mapper.insert(queued);
        assertThat(mapper.requestCancel(queued.getTaskId(), utcNow())).isEqualTo(1);
        AiCallTask cancelled = mapper.selectByTaskId(queued.getTaskId());
        assertThat(cancelled.getState()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancelRequested()).isTrue();

        AiCallTask running = task("task-cancel-running", "idem-cancel-running");
        mapper.insert(running);
        LocalDateTime now = utcNow();
        assertThat(mapper.claimQueued(running.getTaskId(), 0, "live-owner", now.plusSeconds(15), now))
                .isEqualTo(1);
        AiCallTask leased = mapper.selectByTaskId(running.getTaskId());
        assertThat(mapper.transition(running.getTaskId(), leased.getVersion(), "LEASED", "RUNNING",
                "live-owner", now)).isEqualTo(1);
        assertThat(mapper.requestCancel(running.getTaskId(), utcNow())).isEqualTo(1);
        AiCallTask marked = mapper.selectByTaskId(running.getTaskId());
        assertThat(marked.getState()).isEqualTo("RUNNING");
        assertThat(marked.getCancelRequested()).isTrue();
    }

    private AiQueueRecoveryService recovery() {
        return new AiQueueRecoveryService(mapper, attemptMapper, new AiTaskWaitRegistry());
    }

    private AiCallAttempt attempt(AiCallTask task, String attemptId, String state) {
        LocalDateTime now = utcNow();
        AiCallAttempt attempt = new AiCallAttempt();
        attempt.setAttemptId(attemptId);
        attempt.setTaskId(task.getTaskId());
        attempt.setAttemptNo(1);
        attempt.setState(state);
        attempt.setOwner("dead-owner");
        attempt.setPreparedAt(now.minusSeconds(2));
        if ("DISPATCHING".equals(state)) attempt.setSentAt(now.minusSeconds(1));
        attempt.setCreateTime(now);
        attempt.setUpdateTime(now);
        attempt.setDeleted(0);
        return attempt;
    }

    private AiCallTask task(String taskId, String idempotencyKey) {
        LocalDateTime now = utcNow();
        AiCallTask task = new AiCallTask();
        task.setTaskId(taskId);
        task.setCallId("call-" + taskId);
        task.setCallerService("ai-assistant-service");
        task.setIdempotencyKey(idempotencyKey);
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

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
