package com.leetmodel.assistant.rag.retrieval;

import com.leetmodel.assistant.rag.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagRetrieverTest {

    @Test
    void blankAndEmptyResultsReturnNoContext() {
        Fixture fixture = fixture();
        when(fixture.store.search(List.of(1F, 0F, 0F), 8)).thenReturn(List.of());

        assertThat(fixture.retriever.retrieve(" ").chunks()).isEmpty();
        assertThat(fixture.retriever.retrieve("没有命中").chunks()).isEmpty();
        verify(fixture.embeddingModel, never()).embed(" ");
    }

    @Test
    void filtersThresholdDeduplicatesAndAppliesTokenBudgetWithoutReordering() {
        Fixture fixture = fixture();
        fixture.properties.setTokenBudget(5);
        when(fixture.store.search(List.of(1F, 0F, 0F), 8)).thenReturn(List.of(
                hit("c1", "正文一", 0.9, 3, "v1"),
                hit("c1", "正文一重复", 0.89, 3, "v1"),
                hit("c2", "低相关", 0.4, 1, "v1"),
                hit("c3", "超过预算", 0.8, 4, "v1"),
                hit("c4", "正文四", 0.7, 2, "v1")));

        RagRetrievalResult result = fixture.retriever.retrieve("原始问题");

        assertThat(result.chunks()).extracting(RagRetrievedChunk::content)
                .containsExactly("正文一", "正文四");
        assertThat(result.ragIndexVersion()).isEqualTo("v1");
        verify(fixture.embeddingModel).embed("原始问题");
    }

    @Test
    void classifiesEmbeddingFailureAndDimensionDrift() {
        Fixture failed = fixture();
        when(failed.embeddingModel.embed(anyString())).thenThrow(new IllegalStateException("gateway down"));
        assertType(() -> failed.retriever.retrieve("问题"), RagRetrievalException.Type.EMBEDDING);

        Fixture drift = fixture();
        when(drift.embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {1F, 0F})));
        assertType(() -> drift.retriever.retrieve("问题"), RagRetrievalException.Type.DIMENSION);
    }

    @Test
    void classifiesElasticsearchTimeoutAndOtherFailure() {
        Fixture timeout = fixture();
        when(timeout.store.search(List.of(1F, 0F, 0F), 8))
                .thenThrow(new RagStoreException("timeout", true, new SocketTimeoutException()));
        assertType(() -> timeout.retriever.retrieve("问题"), RagRetrievalException.Type.TIMEOUT);

        Fixture failed = fixture();
        when(failed.store.search(List.of(1F, 0F, 0F), 8))
                .thenThrow(new RagStoreException("failed", false, null));
        assertType(() -> failed.retriever.retrieve("问题"), RagRetrievalException.Type.ELASTICSEARCH);
    }

    private void assertType(Runnable invocation, RagRetrievalException.Type type) {
        assertThatThrownBy(invocation::run).isInstanceOfSatisfying(RagRetrievalException.class,
                exception -> assertThat(exception.getType()).isEqualTo(type));
    }

    private Fixture fixture() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimension(3);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RagVectorSearchStore store = mock(RagVectorSearchStore.class);
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[] {1F, 0F, 0F})));
        return new Fixture(properties, embeddingModel, store,
                new RagRetriever(properties, embeddingModel, store));
    }

    private RagVectorHit hit(String chunkId, String content, double score, int tokens, String version) {
        return new RagVectorHit(chunkId, "doc-" + chunkId, content, score,
                "数学建模/文档.md", "标题", version, tokens);
    }

    private record Fixture(RagProperties properties, EmbeddingModel embeddingModel,
                           RagVectorSearchStore store, RagRetriever retriever) {
    }
}
