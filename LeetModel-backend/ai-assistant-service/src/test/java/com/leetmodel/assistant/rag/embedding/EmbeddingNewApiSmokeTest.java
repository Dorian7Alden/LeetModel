package com.leetmodel.assistant.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.leetmodel.common.ai.client.HttpAiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实验证 assistant → common-ai → AI 网关 → new-api 中文 Embedding 与审计链路。 */
@EnabledIfEnvironmentVariable(named = "RUN_NEW_API_SMOKE", matches = "true")
class EmbeddingNewApiSmokeTest {

    @Test
    void shouldEmbedChineseBatchAndExposeAuditedCallId() {
        String gatewayUrl = System.getenv().getOrDefault("AI_GATEWAY_BASE_URL", "http://127.0.0.1:8090");
        RestClient restClient = RestClient.builder().baseUrl(gatewayUrl).build();
        HttpAiClient aiClient = new HttpAiClient(restClient);
        String businessTaskId = "rag-embedding-smoke:" + System.currentTimeMillis();
        AiCallContext context = new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.INDEX_DOCUMENTS, businessTaskId, null, null,
                "MODEL_CFG_RAG_QWEN37_1024_0001", null, AiCallPriority.P4,
                businessTaskId, Instant.now().plusSeconds(120));

        var response = aiClient.embed(new AiEmbeddingRequest("RAG_V1", context, List.of(
                "数学建模中的线性规划适合处理资源分配问题。",
                "最短路径问题可以使用 Dijkstra 算法求解。")));

        assertThat(response.callId()).isNotBlank();
        assertThat(response.model()).isEqualTo("qwen3.7-text-embedding");
        assertThat(response.dimension()).isEqualTo(1024);
        assertThat(response.vectors()).hasSize(2)
                .allSatisfy(vector -> assertThat(vector.values()).hasSize(1024));
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().inputTokens()).isPositive();

        JsonNode audit = restClient.get().uri(uri -> uri.path("/internal/ai/calls")
                        .queryParam("businessTaskId", businessTaskId).queryParam("limit", 1).build())
                .retrieve().body(JsonNode.class);
        assertThat(audit).isNotNull();
        JsonNode row = audit.path("data").path(0);
        assertThat(row.path("callId").asText()).isEqualTo(response.callId());
        assertThat(row.path("callType").asText()).isEqualTo("EMBEDDING");
        assertThat(row.path("inputCount").asInt()).isEqualTo(2);
        assertThat(row.path("vectorDimension").asInt()).isEqualTo(1024);
        assertThat(row.toString()).doesNotContain("线性规划", "Dijkstra", "embeddingVector");
        System.out.printf("embedding-smoke callId=%s model=%s dimension=%d inputTokens=%d%n",
                response.callId(), response.model(), response.dimension(), response.usage().inputTokens());
    }
}
