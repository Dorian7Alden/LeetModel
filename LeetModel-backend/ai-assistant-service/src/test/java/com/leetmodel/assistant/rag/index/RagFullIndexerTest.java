package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.chunk.ChineseKnowledgeChunker;
import com.leetmodel.assistant.rag.chunk.KnowledgeChunk;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.CleanKnowledgeDocument;
import com.leetmodel.assistant.rag.source.KnowledgeDocument;
import com.leetmodel.assistant.rag.source.KnowledgeLoadResult;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFullIndexerTest {

    @Test
    void switchesAliasOnlyAfterEveryBatchSucceedsAndIsRepeatable() {
        Fixture fixture = fixture();
        when(fixture.embeddingModel.embedAll(anyList()))
                .thenReturn(Response.from(List.of(Embedding.from(new float[] {1F, 0F, 0F}))));
        when(fixture.store.writeBatch(any(), anyList(), anyList())).thenReturn(0);

        RagFullIndexSummary first = fixture.indexer.rebuild();
        RagFullIndexSummary second = fixture.indexer.rebuild();

        assertThat(first).isEqualTo(second);
        assertThat(first.documentCount()).isEqualTo(1);
        assertThat(first.chunkCount()).isEqualTo(1);
        assertThat(first.failureCount()).isZero();
        verify(fixture.manager, times(2)).switchReadAlias("physical-index");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VersionedKnowledgeChunk>> batches = ArgumentCaptor.forClass(List.class);
        verify(fixture.store, times(2)).writeBatch(any(), batches.capture(), anyList());
        assertThat(batches.getAllValues().get(0).get(0).chunkId())
                .isEqualTo(batches.getAllValues().get(1).get(0).chunkId());
    }

    @Test
    void embeddingFailureIsSummarizedAndDoesNotSwitchAlias() {
        Fixture fixture = fixture();
        when(fixture.embeddingModel.embedAll(anyList())).thenThrow(new IllegalStateException("provider failed"));

        RagFullIndexSummary summary = fixture.indexer.rebuild();

        assertThat(summary.failureCount()).isEqualTo(1);
        verify(fixture.manager, never()).switchReadAlias(any());
        verify(fixture.store, never()).writeBatch(any(), anyList(), anyList());
    }

    @Test
    void indexCreationFailureIsSummarizedAndDoesNotCallEmbedding() {
        Fixture fixture = fixture();
        when(fixture.manager.ensureIndex(any())).thenThrow(new IllegalStateException("ES unavailable"));

        RagFullIndexSummary summary = fixture.indexer.rebuild();

        assertThat(summary.failureCount()).isEqualTo(1);
        verify(fixture.embeddingModel, never()).embedAll(anyList());
        verify(fixture.manager, never()).switchReadAlias(any());
    }

    private Fixture fixture() {
        RagProperties properties = new RagProperties();
        properties.setKnowledgeBasePath("knowledge-root");
        KnowledgeSourceSelector selector = mock(KnowledgeSourceSelector.class);
        MarkdownKnowledgeLoader loader = mock(MarkdownKnowledgeLoader.class);
        MarkdownKnowledgeCleaner cleaner = mock(MarkdownKnowledgeCleaner.class);
        ChineseKnowledgeChunker chunker = mock(ChineseKnowledgeChunker.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        RagElasticsearchIndexManager manager = mock(RagElasticsearchIndexManager.class);
        RagIndexStore store = mock(RagIndexStore.class);
        KnowledgeDocument document = new KnowledgeDocument("数学建模/A.md", "A", List.of(), null,
                List.of("数学建模"), "hash-a", Instant.EPOCH, 10, "# A\n正文");
        CleanKnowledgeDocument clean = new CleanKnowledgeDocument(document, "【A】\n正文");
        KnowledgeChunk chunk = new KnowledgeChunk(document, 0, clean.content(), 5);
        when(selector.select(any(Path.class))).thenReturn(List.of(Path.of("数学建模/A.md")));
        when(loader.load(any(Path.class), anyList()))
                .thenReturn(new KnowledgeLoadResult(List.of(document), List.of()));
        when(cleaner.clean(document)).thenReturn(clean);
        when(chunker.chunk(clean)).thenReturn(List.of(chunk));
        when(manager.ensureIndex(any())).thenReturn("physical-index");
        RagFullIndexer indexer = new RagFullIndexer(properties, selector, loader, cleaner, chunker,
                new RagIdentityFactory(properties), embeddingModel, manager, store);
        return new Fixture(indexer, embeddingModel, manager, store);
    }

    private record Fixture(RagFullIndexer indexer, EmbeddingModel embeddingModel,
                           RagElasticsearchIndexManager manager, RagIndexStore store) {
    }
}
