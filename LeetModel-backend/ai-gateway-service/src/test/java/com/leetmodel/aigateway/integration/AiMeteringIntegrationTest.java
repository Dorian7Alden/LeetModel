package com.leetmodel.aigateway.integration;

import com.leetmodel.aigateway.service.AiCallAuditService;
import com.leetmodel.aigateway.service.AiChatService;
import com.leetmodel.aigateway.service.AiCostEnrichmentService;
import com.leetmodel.aigateway.service.AiEmbeddingService;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:ai-call-log-test-schema.sql",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "ai.cost-enrichment.poll-delay-ms=3600000"
})
class AiMeteringIntegrationTest {
    private static final AtomicReference<MockResponse> RESPONSE = new AtomicReference<>();
    private static HttpServer server;

    @Autowired private AiChatService chatService;
    @Autowired private AiCallAuditService auditService;
    @Autowired private AiCostEnrichmentService costEnrichmentService;
    @Autowired private AiEmbeddingService embeddingService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        startServer();
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:ai_metering;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("ai.new-api.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        registry.add("ai.new-api.relay-token", () -> "integration-test-token");
        registry.add("ai.cost-enrichment.max-attempts", () -> "2");
        registry.add("ai.cost-enrichment.retry-delay", () -> "1ms");
        registry.add("ai.cost-enrichment.snapshots[deepseek-v4-flash].version", () -> "PRICE_TEST_0001");
        registry.add("ai.cost-enrichment.snapshots[deepseek-v4-flash].currency", () -> "CNY");
        registry.add("ai.cost-enrichment.snapshots[deepseek-v4-flash].input-per-million-tokens", () -> "2.00");
        registry.add("ai.cost-enrichment.snapshots[deepseek-v4-flash].output-per-million-tokens", () -> "4.00");
        registry.add("ai.gateway.embedding-models[RAG_V1].provider", () -> "NEW_API");
        registry.add("ai.gateway.embedding-models[RAG_V1].model", () -> "embedding-model");
        registry.add("ai.gateway.embedding-models[RAG_V1].dimension", () -> "2");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void shouldMeterMockNewApiFailuresDelayedCostAndFilteredAggregation() throws Exception {
        RESPONSE.set(new MockResponse(200, """
                {"id":"relay-complete","model":"deepseek-v4-flash",
                 "choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                """));
        var complete = chatService.chat(request("task:1", "evaluation:1"));

        RESPONSE.set(new MockResponse(200, """
                {"id":"relay-no-usage","model":"deepseek-v4-flash",
                 "choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                """));
        chatService.chat(request("task:2", "evaluation:2"));

        RESPONSE.set(new MockResponse(429,
                "{\"error\":{\"type\":\"rate_limit_error\"}}"));
        assertThatThrownBy(() -> chatService.chat(request("task:3", "evaluation:3")))
                .isInstanceOf(BusinessException.class);

        AiCallQueryDTO evaluationQuery = new AiCallQueryDTO();
        evaluationQuery.setEvaluationTaskId("evaluation:1");
        var before = auditService.list(evaluationQuery);
        assertThat(before).singleElement().satisfies(row -> {
            assertThat(row.getCallId()).isEqualTo(complete.callId());
            assertThat(row.getProviderResponseId()).isEqualTo("relay-complete");
            assertThat(row.getNewApiRequestId()).isNull();
            assertThat(row.getCostSource()).isEqualTo("UNKNOWN");
        });

        costEnrichmentService.enrichDue();
        costEnrichmentService.enrichDue();

        var after = auditService.list(evaluationQuery);
        assertThat(after).singleElement().satisfies(row -> {
            assertThat(row.getCostSource()).isEqualTo("PRICE_SNAPSHOT_ESTIMATED");
            assertThat(row.getCostAmount()).isEqualByComparingTo("0.000040000000");
            assertThat(row.getPriceSnapshotVersion()).isEqualTo("PRICE_TEST_0001");
        });

        var stats = auditService.stats(new AiCallQueryDTO());
        assertThat(stats.getTotalCount()).isEqualTo(3L);
        assertThat(stats.getSuccessCount()).isEqualTo(2L);
        assertThat(stats.getFailureCount()).isEqualTo(1L);
        assertThat(stats.getTotalTokens()).isEqualTo(15L);
        assertThat(stats.getEstimatedCostCount()).isEqualTo(1L);
        assertThat(stats.getUnknownCostCount()).isEqualTo(2L);
        assertThat(stats.getKnownCostAmount()).isEqualByComparingTo("0.000040000000");
        assertThat(stats.getCostCurrency()).isEqualTo("CNY");

        RESPONSE.set(new MockResponse(200, """
                {"model":"embedding-model","data":[
                  {"index":0,"embedding":[0.1,0.2]},
                  {"index":1,"embedding":[0.3,0.4]}],
                 "usage":{"prompt_tokens":6,"total_tokens":6}}
                """));
        AiCallContext embeddingContext = new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.INDEX_DOCUMENTS, "rag-index:1", null, null,
                "MODEL_CFG_RAG_V1", null, AiCallPriority.P4, "rag-index:1",
                Instant.parse("2099-01-01T00:00:00Z"));
        var embedding = embeddingService.embed(new AiEmbeddingRequest("RAG_V1", embeddingContext,
                List.of("中文片段一", "中文片段二")));
        AiCallQueryDTO embeddingQuery = new AiCallQueryDTO();
        embeddingQuery.setBusinessTaskId("rag-index:1");
        assertThat(auditService.list(embeddingQuery)).singleElement().satisfies(row -> {
            assertThat(row.getCallId()).isEqualTo(embedding.callId());
            assertThat(row.getCallType()).isEqualTo("EMBEDDING");
            assertThat(row.getInputCount()).isEqualTo(2);
            assertThat(row.getVectorDimension()).isEqualTo(2);
            assertThat(row.getInputTokens()).isEqualTo(6L);
        });
    }

    private AiChatRequest request(String businessTaskId, String evaluationTaskId) {
        AiCallContext context = new AiCallContext("ai-review-service", AiFeatureCode.PAPER_REVIEW,
                AiOperationCode.EXPERIMENT_REVIEW, businessTaskId, "BASIC_REVIEW_V1",
                "PROMPT_BASIC_REVIEW_0001", "MODEL_CFG_REVIEW_TEXT_TEST_0001",
                evaluationTaskId, AiCallPriority.P3, "integration:" + businessTaskId,
                Instant.parse("2099-01-01T00:00:00Z"));
        return new AiChatRequest(AiModality.TEXT, context,
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "synthetic", null)))),
                64, null, null, false);
    }

    private static synchronized void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", AiMeteringIntegrationTest::respond);
            server.createContext("/v1/embeddings", AiMeteringIntegrationTest::respond);
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 mock new-api", exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        MockResponse response = RESPONSE.get();
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record MockResponse(int status, String body) {}
}
