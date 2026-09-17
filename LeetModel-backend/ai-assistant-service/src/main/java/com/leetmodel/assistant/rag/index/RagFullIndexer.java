package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.chunk.ChineseKnowledgeChunker;
import com.leetmodel.assistant.rag.chunk.KnowledgeChunk;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.KnowledgeLoadResult;
import com.leetmodel.assistant.rag.source.KnowledgeSourceSelector;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeCleaner;
import com.leetmodel.assistant.rag.source.MarkdownKnowledgeLoader;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 全量构建独立物理索引，仅在全部步骤成功后切换读别名。 */
@Component
@ConditionalOnBean(RagElasticsearchIndexManager.class)
public class RagFullIndexer {

    private final RagProperties properties;
    private final KnowledgeSourceSelector selector;
    private final MarkdownKnowledgeLoader loader;
    private final MarkdownKnowledgeCleaner cleaner;
    private final ChineseKnowledgeChunker chunker;
    private final RagIdentityFactory identityFactory;
    private final EmbeddingModel embeddingModel;
    private final RagElasticsearchIndexManager indexManager;
    private final RagIndexStore indexStore;

    public RagFullIndexer(RagProperties properties, KnowledgeSourceSelector selector, MarkdownKnowledgeLoader loader,
                          MarkdownKnowledgeCleaner cleaner, ChineseKnowledgeChunker chunker,
                          RagIdentityFactory identityFactory,
                          @Qualifier("ragIndexEmbeddingModel") EmbeddingModel embeddingModel,
                          RagElasticsearchIndexManager indexManager, RagIndexStore indexStore) {
        this.properties = properties;
        this.selector = selector;
        this.loader = loader;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.identityFactory = identityFactory;
        this.embeddingModel = embeddingModel;
        this.indexManager = indexManager;
        this.indexStore = indexStore;
    }

    public RagFullIndexSummary rebuild() {
        Path root = Path.of(properties.getKnowledgeBasePath());
        KnowledgeLoadResult loaded = loader.load(root, selector.select(root));
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int failures = loaded.failures().size();
        for (var document : loaded.documents()) {
            try {
                chunks.addAll(chunker.chunk(cleaner.clean(document)));
            } catch (RuntimeException exception) {
                failures++;
            }
        }
        RagVersionSet versions = identityFactory.versions(chunks);
        if (failures > 0 || chunks.isEmpty()) {
            return summary(loaded.documents().size(), chunks.size(), Math.max(1, failures), versions);
        }

        List<VersionedKnowledgeChunk> versioned = identityFactory.version(chunks);
        String indexName;
        try {
            indexName = indexManager.ensureIndex(versions);
        } catch (RuntimeException exception) {
            return summary(loaded.documents().size(), chunks.size(), chunks.size(), versions);
        }
        for (int start = 0; start < versioned.size(); start += properties.getEmbeddingBatchSize()) {
            int end = Math.min(start + properties.getEmbeddingBatchSize(), versioned.size());
            List<VersionedKnowledgeChunk> batch = versioned.subList(start, end);
            try {
                List<TextSegment> segments = batch.stream()
                        .map(item -> TextSegment.from(item.chunk().content())).toList();
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                failures += indexStore.writeBatch(indexName, batch, embeddings);
            } catch (RuntimeException exception) {
                failures += batch.size();
            }
        }
        if (failures == 0) {
            indexManager.switchReadAlias(indexName);
        }
        return summary(loaded.documents().size(), chunks.size(), failures, versions);
    }

    private RagFullIndexSummary summary(int documents, int chunks, int failures, RagVersionSet versions) {
        return new RagFullIndexSummary(documents, chunks, failures, versions.ragIndexVersion());
    }
}
