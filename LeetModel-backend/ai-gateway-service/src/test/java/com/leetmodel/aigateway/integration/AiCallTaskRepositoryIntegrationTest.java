package com.leetmodel.aigateway.integration;

import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
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
        "ai.cost-enrichment.poll-delay-ms=3600000"
})
class AiCallTaskRepositoryIntegrationTest {

    @Autowired
    private AiCallTaskMapper mapper;

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
                    LocalDateTime now = LocalDateTime.now();
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
                leased.getLeaseOwner(), LocalDateTime.now())).isEqualTo(1);
        assertThat(mapper.transition(leased.getTaskId(), leased.getVersion(), "LEASED", "RUNNING",
                leased.getLeaseOwner(), LocalDateTime.now())).isZero();
    }

    @Test
    void callerIdempotencyIsQueryable() {
        AiCallTask task = task("task-idempotent", "idem-idempotent");
        mapper.insert(task);
        assertThat(mapper.selectByIdempotency("ai-assistant-service", "idem-idempotent").getTaskId())
                .isEqualTo("task-idempotent");
    }

    private AiCallTask task(String taskId, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
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
}
