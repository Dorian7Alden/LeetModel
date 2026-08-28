package com.leetmodel.assistant.rag.index;

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
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RAG_ES_INTEGRATION", matches = "true")
class RagIncrementalIndexIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void synchronizesAddModifyDeleteAndRecoversAfterInterruptedEmbedding() throws Exception {
        write("数学建模/A.md", "# A\n\n旧内容");
        write("数学建模/B.md", "# B\n\n将被删除");
        write("数学建模/D.md", "# D\n\n保持不变");
        RagProperties properties = properties();
        ObjectMapper objectMapper = new ObjectMapper();
        try (RestClient client = RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build()) {
            Components components = components(properties, client, objectMapper);
            RagFullIndexSummary baseline = fullIndexer(properties, components, embedding()).rebuild();
            assertThat(baseline.failureCount()).isZero();
            String baselineTarget = aliasTarget(client, objectMapper, properties.getIndexAlias());

            write("数学建模/A.md", "# A\n\n修改后的内容");
            Files.delete(tempDir.resolve("数学建模/B.md"));
            write("数学建模/C.md", "# C\n\n新增内容");
            EmbeddingModel failing = segments -> {
                throw new IllegalStateException("simulated interruption");
            };
            RagIncrementalIndexSummary interrupted = incrementalIndexer(properties, components, failing).update();
            assertThat(interrupted.failureCount()).isPositive();
            assertThat(aliasTarget(client, objectMapper, properties.getIndexAlias())).isEqualTo(baselineTarget);

            RagIncrementalIndexSummary recovered = incrementalIndexer(properties, components, embedding()).update();
            assertThat(recovered.addedCount()).isEqualTo(1);
            assertThat(recovered.modifiedCount()).isEqualTo(1);
            assertThat(recovered.deletedCount()).isEqualTo(1);
            assertThat(recovered.failureCount()).isZero();
            Map<String, RagManifestDocument> manifest = components.incrementalStore
                    .readManifest(properties.getIndexAlias());
            assertThat(manifest.values()).extracting(RagManifestDocument::sourcePath)
                    .containsExactlyInAnyOrder("数学建模/A.md", "数学建模/C.md", "数学建模/D.md");

            RagIncrementalIndexSummary repeated = incrementalIndexer(properties, components, embedding()).update();
            assertThat(repeated.addedCount()).isZero();
            assertThat(repeated.modifiedCount()).isZero();
            assertThat(repeated.deletedCount()).isZero();
            assertThat(repeated.failureCount()).isZero();
        }
    }

    private RagProperties properties() {
        RagProperties properties = new RagProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        properties.setIndexAlias("leetmodel-rag-s4-11-test-read");
        properties.setEmbeddingDimension(3);
        properties.setEmbeddingModelVersion("deterministic-incremental@3");
        return properties;
    }

    private Components components(RagProperties properties, RestClient client, ObjectMapper objectMapper) {
        KnowledgeSourceSelector selector = new KnowledgeSourceSelector(properties);
        return new Components(selector, new MarkdownKnowledgeLoader(selector), new MarkdownKnowledgeCleaner(),
                new ChineseKnowledgeChunker(properties, new ChineseTokenEstimator()),
                new RagIdentityFactory(properties), new RagElasticsearchIndexManager(client, objectMapper, properties),
                new ElasticsearchRagIndexStore(client, objectMapper),
                new ElasticsearchRagIncrementalStore(client, objectMapper));
    }

    private RagFullIndexer fullIndexer(RagProperties properties, Components components, EmbeddingModel model) {
        return new RagFullIndexer(properties, components.selector, components.loader, components.cleaner,
                components.chunker, components.identityFactory, model, components.manager, components.indexStore);
    }

    private RagIncrementalIndexer incrementalIndexer(RagProperties properties, Components components,
                                                      EmbeddingModel model) {
        return new RagIncrementalIndexer(properties, components.selector, components.loader, components.cleaner,
                components.chunker, components.identityFactory, model, components.manager,
                components.indexStore, components.incrementalStore);
    }

    private EmbeddingModel embedding() {
        return segments -> dev.langchain4j.model.output.Response.from(segments.stream()
                .map(segment -> Embedding.from(new float[] {1F, 0F, 0F})).toList());
    }

    private String aliasTarget(RestClient client, ObjectMapper objectMapper, String alias) throws Exception {
        var response = client.performRequest(new Request("GET", "/_alias/" + alias));
        return objectMapper.readTree(response.getEntity().getContent()).fieldNames().next();
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private record Components(
            KnowledgeSourceSelector selector,
            MarkdownKnowledgeLoader loader,
            MarkdownKnowledgeCleaner cleaner,
            ChineseKnowledgeChunker chunker,
            RagIdentityFactory identityFactory,
            RagElasticsearchIndexManager manager,
            RagIndexStore indexStore,
            RagIncrementalStore incrementalStore) {
    }
}
