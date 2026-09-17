package com.leetmodel.assistant.rag.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.chunk.ChineseKnowledgeChunker;
import com.leetmodel.assistant.rag.chunk.ChineseTokenEstimator;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.embedding.CommonAiEmbeddingModel;
import com.leetmodel.assistant.rag.index.ElasticsearchRagIndexStore;
import com.leetmodel.assistant.rag.index.RagElasticsearchIndexManager;
import com.leetmodel.assistant.rag.index.RagFullIndexer;
import com.leetmodel.assistant.rag.index.RagIdentityFactory;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import com.leetmodel.common.ai.client.HttpAiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.ResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient.Builder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 真实验证 common-ai → AI 网关 → new-api → ES → RAG 召回，只操作独立测试索引。 */
@EnabledIfEnvironmentVariable(named = "RUN_RAG_E2E_SMOKE", matches = "true")
class RagNewApiEndToEndSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void indexesAndRetrievesWithRealEmbeddingAndRecordsBaseline() throws Exception {
        write("数学建模/模型/线性规划.md", "# 线性规划\n\n线性规划通过决策变量、目标函数和线性约束处理资源分配问题。");
        write("数学建模/模型/图论.md", "# 图论\n\nDijkstra 算法适合求解非负权图中的单源最短路径。");
        long runId = System.currentTimeMillis();
        RagProperties properties = properties(runId);
        ObjectMapper objectMapper = new ObjectMapper();
        Builder gateway = org.springframework.web.client.RestClient.builder()
                .baseUrl(System.getenv().getOrDefault("AI_GATEWAY_BASE_URL", "http://127.0.0.1:8090"));
        HttpAiClient aiClient = new HttpAiClient(gateway.build());
        AtomicInteger calls = new AtomicInteger();
        AtomicLong inputTokens = new AtomicLong();
        EmbeddingModel indexModel = meteredModel(aiClient, AiOperationCode.INDEX_DOCUMENTS,
                "rag-e2e-index-" + runId, calls, inputTokens);
        EmbeddingModel queryModel = meteredModel(aiClient, AiOperationCode.RETRIEVE_CONTEXT,
                "rag-e2e-query-" + runId, calls, inputTokens);

        try (RestClient elasticsearch = RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build()) {
            try {
                KnowledgeSourceSelector selector = new KnowledgeSourceSelector(properties);
                RagElasticsearchIndexManager manager = new RagElasticsearchIndexManager(
                        elasticsearch, objectMapper, properties);
                long startedAt = System.nanoTime();
                var summary = new RagFullIndexer(properties, selector, new MarkdownKnowledgeLoader(selector),
                        new MarkdownKnowledgeCleaner(),
                        new ChineseKnowledgeChunker(properties, new ChineseTokenEstimator()),
                        new RagIdentityFactory(properties), indexModel, manager,
                        new ElasticsearchRagIndexStore(elasticsearch, objectMapper)).rebuild();
                RagRetrievalResult result = new RagRetriever(properties, queryModel,
                        new ElasticsearchRagVectorSearchStore(elasticsearch, objectMapper, properties))
                        .retrieve("怎样使用决策变量和约束来优化资源分配？");
                long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
                int contextTokens = result.chunks().stream().mapToInt(RagRetrievedChunk::estimatedTokens).sum();

                assertThat(summary.failureCount()).isZero();
                assertThat(result.chunks()).hasSize(1);
                assertThat(result.chunks().get(0).sourcePath()).isEqualTo("数学建模/模型/线性规划.md");
                assertThat(result.ragIndexVersion()).isEqualTo(summary.ragIndexVersion());
                assertThat(inputTokens.get()).isPositive();
                assertThat(contextTokens).isPositive();
                System.out.printf("rag-e2e durationMs=%d embeddingCalls=%d embeddingInputTokens=%d "
                                + "contextTokenIncrement=%d recallAt1=1/1 topSource=%s%n",
                        durationMs, calls.get(), inputTokens.get(), contextTokens,
                        result.chunks().get(0).sourcePath());
            } finally {
                deleteTestIndices(elasticsearch, objectMapper, properties.getIndexAlias());
            }
        }
    }

    private RagProperties properties(long runId) {
        RagProperties properties = new RagProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        properties.setIndexAlias("leetmodel-rag-s4-15-e2e-" + runId + "-read");
        properties.setScoreThreshold(0.5);
        properties.setTopK(1);
        return properties;
    }

    private EmbeddingModel meteredModel(HttpAiClient aiClient, AiOperationCode operation, String taskId,
                                        AtomicInteger calls, AtomicLong inputTokens) {
        AtomicInteger sequence = new AtomicInteger();
        CommonAiEmbeddingModel delegate = new CommonAiEmbeddingModel(aiClient, "RAG_V1", 1024, segments ->
                context(operation, taskId, sequence.incrementAndGet()));
        return new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                Response<List<Embedding>> response = delegate.embedAll(segments);
                calls.incrementAndGet();
                if (response.tokenUsage() != null && response.tokenUsage().inputTokenCount() != null) {
                    inputTokens.addAndGet(response.tokenUsage().inputTokenCount());
                }
                return response;
            }

            @Override
            public int dimension() {
                return delegate.dimension();
            }
        };
    }

    private AiCallContext context(AiOperationCode operation, String taskId, int sequence) {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG, operation, taskId,
                null, null, "MODEL_CFG_RAG_QWEN37_1024_0001", null, null,
                AiCallPriority.P4, taskId + '-' + sequence, Instant.now().plusSeconds(120));
    }

    private void deleteTestIndices(RestClient client, ObjectMapper objectMapper, String alias) throws Exception {
        org.elasticsearch.client.Response response;
        try {
            response = client.performRequest(new Request("GET", "/_alias/" + alias));
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw exception;
        }
        JsonNode aliases = objectMapper.readTree(response.getEntity().getContent());
        for (var names = aliases.fieldNames(); names.hasNext();) {
            String index = names.next();
            if (!index.startsWith("leetmodel-rag-s4-15-e2e-")) {
                throw new IllegalStateException("拒绝清理非测试索引");
            }
            client.performRequest(new Request("DELETE", "/" + index));
        }
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
