package com.leetmodel.assistant.rag.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.chunk.ChineseKnowledgeChunker;
import com.leetmodel.assistant.rag.chunk.ChineseTokenEstimator;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.index.ElasticsearchRagIndexStore;
import com.leetmodel.assistant.rag.index.RagElasticsearchIndexManager;
import com.leetmodel.assistant.rag.index.RagFullIndexer;
import com.leetmodel.assistant.rag.index.RagIdentityFactory;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RAG_ES_INTEGRATION", matches = "true")
class RagRetrieverIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void retrievesFromIndependentElasticsearchAliasAndFiltersLowScores() throws Exception {
        Path file = tempDir.resolve("数学建模/模型/线性规划.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "# 线性规划\n\n通过决策变量、目标函数和约束条件建立优化模型。");
        RagProperties properties = new RagProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        properties.setIndexAlias("leetmodel-rag-s4-12-test-read");
        properties.setEmbeddingDimension(3);
        properties.setEmbeddingModelVersion("deterministic-retrieval@3");
        properties.setScoreThreshold(0.65);
        ObjectMapper objectMapper = new ObjectMapper();
        EmbeddingModel model = segments -> dev.langchain4j.model.output.Response.from(segments.stream()
                .map(segment -> Embedding.from(new float[] {1F, 0F, 0F})).toList());
        try (RestClient client = RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build()) {
            KnowledgeSourceSelector selector = new KnowledgeSourceSelector(properties);
            RagElasticsearchIndexManager manager = new RagElasticsearchIndexManager(client, objectMapper, properties);
            RagFullIndexer indexer = new RagFullIndexer(properties, selector, new MarkdownKnowledgeLoader(selector),
                    new MarkdownKnowledgeCleaner(),
                    new ChineseKnowledgeChunker(properties, new ChineseTokenEstimator()),
                    new RagIdentityFactory(properties), model, manager,
                    new ElasticsearchRagIndexStore(client, objectMapper));
            assertThat(indexer.rebuild().failureCount()).isZero();

            RagRetriever retriever = new RagRetriever(properties, model,
                    new ElasticsearchRagVectorSearchStore(client, objectMapper, properties));
            RagRetrievalResult hit = retriever.retrieve("怎样建立线性规划模型？");
            assertThat(hit.chunks()).isNotEmpty().first().satisfies(chunk -> {
                assertThat(chunk.content()).contains("决策变量", "目标函数", "约束条件");
                assertThat(chunk.sourcePath()).isEqualTo("数学建模/模型/线性规划.md");
                assertThat(chunk.title()).isEqualTo("线性规划");
                assertThat(chunk.score()).isGreaterThanOrEqualTo(0.99);
            });

            properties.setScoreThreshold(1.01);
            assertThat(retriever.retrieve("怎样建立线性规划模型？").chunks()).isEmpty();
        }
    }
}
