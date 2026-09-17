package com.leetmodel.assistant.benchmark;

import com.leetmodel.common.ai.client.HttpAiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** S5-01 本地真实小负载基线；禁止用于生产或扩大并发。 */
@EnabledIfEnvironmentVariable(named = "RUN_AI_SCHEDULING_BASELINE", matches = "true")
class AiGatewayNoQueueBaselineSmokeTest {

    private static final int SERIAL_SAMPLES = 5;
    private static final int CONCURRENT_SAMPLES = 6;
    private static final int CONCURRENCY = 2;
    private static final String TINY_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void recordsSingleAndSmallConcurrencyWithoutQueue() throws Exception {
        String gatewayUrl = System.getenv().getOrDefault("AI_GATEWAY_BASE_URL", "http://127.0.0.1:8090");
        HttpAiClient client = new HttpAiClient(RestClient.builder().baseUrl(gatewayUrl).build());
        List<Workload> workloads = List.of(
                new Workload("assistant-text-p0", () -> text(client, AiFeatureCode.AI_ASSISTANT,
                        AiOperationCode.CHAT_REPLY, AiCallPriority.P0, "MODEL_CFG_ASSISTANT_TEXT_0001")),
                new Workload("formal-review-multimodal-p1", () -> multimodal(client,
                        AiOperationCode.FORMAL_REVIEW, AiCallPriority.P1)),
                new Workload("experiment-review-multimodal-p3", () -> multimodal(client,
                        AiOperationCode.EXPERIMENT_REVIEW, AiCallPriority.P3)),
                new Workload("rag-index-embedding-p4", () -> embedding(client)));

        List<Baseline> baselines = new ArrayList<>();
        for (Workload workload : workloads) {
            baselines.add(run(workload, 1, SERIAL_SAMPLES));
            baselines.add(run(workload, CONCURRENCY, CONCURRENT_SAMPLES));
        }
        baselines.forEach(System.out::println);
        assertThat(baselines).allSatisfy(result -> {
            assertThat(result.samples()).isPositive();
            assertThat(result.p50Ms()).isPositive();
            assertThat(result.p95Ms()).isGreaterThanOrEqualTo(result.p50Ms());
        });
    }

    private Baseline run(Workload workload, int concurrency, int samples) throws Exception {
        List<Sample> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Sample>> futures = new ArrayList<>();
            for (int index = 0; index < samples; index++) {
                futures.add(executor.submit(measure(workload.action())));
            }
            for (Future<Sample> future : futures) results.add(future.get());
        } finally {
            executor.shutdownNow();
        }
        List<Long> durations = results.stream().map(Sample::durationMs).sorted().toList();
        long failures = results.stream().filter(sample -> sample.failureType() != null).count();
        long timeouts = results.stream().filter(sample -> "TIMEOUT".equals(sample.failureType())).count();
        long rateLimits = results.stream().filter(sample -> "RATE_LIMITED".equals(sample.failureType())).count();
        return new Baseline(workload.name(), concurrency, samples, percentile(durations, 0.50),
                percentile(durations, 0.95), failures, timeouts, rateLimits);
    }

    private Callable<Sample> measure(ThrowingAction action) {
        return () -> {
            long startedAt = System.nanoTime();
            try {
                action.run();
                return new Sample(elapsed(startedAt), null);
            } catch (RuntimeException exception) {
                return new Sample(elapsed(startedAt), classify(exception));
            }
        };
    }

    private void text(HttpAiClient client, AiFeatureCode feature, AiOperationCode operation,
                      AiCallPriority priority, String modelVersion) {
        String id = UUID.randomUUID().toString();
        client.chat(new AiChatRequest(AiModality.TEXT,
                context(feature, operation, priority, modelVersion, id),
                List.of(message(List.of(new AiContentPart(AiContentType.TEXT,
                        "用一句短句解释线性规划。", null)))), 48, 0.0, AiResponseFormat.TEXT, false));
    }

    private void multimodal(HttpAiClient client, AiOperationCode operation, AiCallPriority priority) {
        String id = UUID.randomUUID().toString();
        client.chat(new AiChatRequest(AiModality.MULTIMODAL,
                context(AiFeatureCode.PAPER_REVIEW, operation, priority,
                        "MODEL_CFG_REVIEW_MULTIMODAL_0001", id),
                List.of(message(List.of(
                        new AiContentPart(AiContentType.TEXT, "只回答图片是否可见。", null),
                        new AiContentPart(AiContentType.IMAGE_URL, null, TINY_PNG)))),
                48, 0.0, AiResponseFormat.TEXT, false));
    }

    private void embedding(HttpAiClient client) {
        String id = UUID.randomUUID().toString();
        client.embed(new AiEmbeddingRequest("RAG_V1",
                context(AiFeatureCode.RAG, AiOperationCode.INDEX_DOCUMENTS, AiCallPriority.P4,
                        "MODEL_CFG_RAG_QWEN37_1024_0001", id),
                List.of("线性规划通过目标函数和约束条件描述资源分配。")));
    }

    private AiCallContext context(AiFeatureCode feature, AiOperationCode operation,
                                  AiCallPriority priority, String modelVersion, String id) {
        return new AiCallContext("s5-baseline", feature, operation, "baseline:" + id,
                "S5_BASELINE_V1", null, modelVersion, null, priority,
                "s5-baseline:" + id, Instant.now().plusSeconds(120));
    }

    private AiMessage message(List<AiContentPart> content) {
        return new AiMessage(AiRole.USER, content);
    }

    private long elapsed(long startedAt) {
        return Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private long percentile(List<Long> sorted, double quantile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1);
        return sorted.get(index);
    }

    private String classify(RuntimeException exception) {
        String value = (exception.getClass().getSimpleName() + ' ' + exception.getMessage())
                .toUpperCase(Locale.ROOT);
        if (value.contains("429") || value.contains("RATE_LIMIT")) return "RATE_LIMITED";
        if (value.contains("TIMEOUT") || value.contains("TIMED OUT")) return "TIMEOUT";
        return "OTHER";
    }

    private record Workload(String name, ThrowingAction action) {}

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }

    private record Sample(long durationMs, String failureType) {}

    private record Baseline(String workload, int concurrency, int samples, long p50Ms, long p95Ms,
                            long failures, long timeouts, long rateLimits) {
        @Override
        public String toString() {
            return "s5-baseline workload=" + workload + " concurrency=" + concurrency
                    + " samples=" + samples + " p50Ms=" + p50Ms + " p95Ms=" + p95Ms
                    + " failureRate=" + failures + '/' + samples + " timeouts=" + timeouts
                    + " rateLimits=" + rateLimits;
        }
    }
}
