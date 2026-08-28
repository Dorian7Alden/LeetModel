package com.leetmodel.assistant.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.chunk.ChineseKnowledgeChunker;
import com.leetmodel.assistant.rag.chunk.ChineseTokenEstimator;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RAG_ES_INTEGRATION", matches = "true")
class RagFullIndexIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void fullIndexIsIdempotentAndSwitchesAliasAfterSuccess() throws Exception {
        write("数学建模/模型/A.md", "# 线性规划\n\n建立变量和约束。");
        write("数学建模/经验/B.md", "# 比赛经验\n\n先读题再分工。");
        RagProperties properties = new RagProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        properties.setIndexAlias("leetmodel-rag-s4-10-test-read");
        properties.setEmbeddingDimension(3);
        properties.setEmbeddingModelVersion("deterministic-test@3");
        ObjectMapper objectMapper = new ObjectMapper();
        try (RestClient client = RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build()) {
            KnowledgeSourceSelector selector = new KnowledgeSourceSelector(properties);
            MarkdownKnowledgeLoader loader = new MarkdownKnowledgeLoader(selector);
            RagElasticsearchIndexManager manager = new RagElasticsearchIndexManager(client, objectMapper, properties);
            EmbeddingModel embedding = segments -> dev.langchain4j.model.output.Response.from(segments.stream()
                    .map(segment -> Embedding.from(new float[] {1F, 0F, 0F})).toList());
            RagFullIndexer indexer = new RagFullIndexer(properties, selector, loader,
                    new MarkdownKnowledgeCleaner(),
                    new ChineseKnowledgeChunker(properties, new ChineseTokenEstimator()),
                    new RagIdentityFactory(properties), embedding, manager,
                    new ElasticsearchRagIndexStore(client, objectMapper));

            RagFullIndexSummary first = indexer.rebuild();
            RagFullIndexSummary second = indexer.rebuild();

            assertThat(first).isEqualTo(second);
            assertThat(first.documentCount()).isEqualTo(2);
            assertThat(first.failureCount()).isZero();
            String indexName = manager.physicalIndexName(first.ragIndexVersion());
            assertThat(count(client, objectMapper, indexName)).isEqualTo(first.chunkCount());
            assertThat(aliasTarget(client, objectMapper, properties.getIndexAlias())).isEqualTo(indexName);
        }
    }

    private long count(RestClient client, ObjectMapper objectMapper, String index) throws Exception {
        Response response = client.performRequest(new Request("GET", "/" + index + "/_count"));
        return objectMapper.readTree(response.getEntity().getContent()).path("count").asLong();
    }

    private String aliasTarget(RestClient client, ObjectMapper objectMapper, String alias) throws Exception {
        Response response = client.performRequest(new Request("GET", "/_alias/" + alias));
        JsonNode responseBody = objectMapper.readTree(response.getEntity().getContent());
        return responseBody.fieldNames().next();
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
