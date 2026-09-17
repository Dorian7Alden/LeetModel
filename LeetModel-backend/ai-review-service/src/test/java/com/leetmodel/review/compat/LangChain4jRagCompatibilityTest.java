package com.leetmodel.review.compat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jRagCompatibilityTest {

    @Test
    void shouldIngestSplitAndRetrieveMarkdownWithCustomEmbeddingModel() {
        EmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(80, 10))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(Document.from("""
                # 线性规划

                单纯形法用于求解线性规划，并从可行顶点寻找最优解。

                # 图论

                Dijkstra 算法用于求解非负权图的最短路径。
                """));

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(1)
                .minScore(0.8)
                .build();

        List<Content> result = retriever.retrieve(Query.from("怎样求解线性规划？"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).textSegment().text()).contains("单纯形法");
    }

    private static final class KeywordEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(TextSegment::text)
                    .map(KeywordEmbeddingModel::embedText)
                    .toList());
        }

        @Override
        public int dimension() {
            return 3;
        }

        private static Embedding embedText(String text) {
            if (text.contains("线性规划") || text.contains("单纯形")) {
                return Embedding.from(new float[]{1, 0, 0});
            }
            if (text.contains("图论") || text.contains("Dijkstra") || text.contains("最短路径")) {
                return Embedding.from(new float[]{0, 1, 0});
            }
            return Embedding.from(new float[]{0, 0, 1});
        }
    }
}
