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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 通过新快照索引实现可重试的新增、修改和删除同步。 */
@Component
@ConditionalOnBean(RagElasticsearchIndexManager.class)
public class RagIncrementalIndexer {

    private final RagProperties properties;
    private final KnowledgeSourceSelector selector;
    private final MarkdownKnowledgeLoader loader;
    private final MarkdownKnowledgeCleaner cleaner;
    private final ChineseKnowledgeChunker chunker;
    private final RagIdentityFactory identityFactory;
    private final EmbeddingModel embeddingModel;
    private final RagElasticsearchIndexManager indexManager;
    private final RagIndexStore indexStore;
    private final RagIncrementalStore incrementalStore;

    public RagIncrementalIndexer(RagProperties properties, KnowledgeSourceSelector selector,
                                 MarkdownKnowledgeLoader loader, MarkdownKnowledgeCleaner cleaner,
                                 ChineseKnowledgeChunker chunker, RagIdentityFactory identityFactory,
                                 @Qualifier("ragIndexEmbeddingModel") EmbeddingModel embeddingModel,
                                 RagElasticsearchIndexManager indexManager, RagIndexStore indexStore,
                                 RagIncrementalStore incrementalStore) {
        this.properties = properties;
        this.selector = selector;
        this.loader = loader;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.identityFactory = identityFactory;
        this.embeddingModel = embeddingModel;
        this.indexManager = indexManager;
        this.indexStore = indexStore;
        this.incrementalStore = incrementalStore;
    }

    public RagIncrementalIndexSummary update() {
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
            return summary(loaded.documents().size(), chunks.size(), 0, 0, 0,
                    Math.max(1, failures), versions);
        }
        List<VersionedKnowledgeChunk> versioned = identityFactory.version(chunks);
        Map<String, RagManifestDocument> previous = incrementalStore.readManifest(properties.getIndexAlias());
        if (previous.isEmpty()) {
            return fullFallback(loaded.documents().size(), versioned, versions);
        }

        Map<String, VersionedKnowledgeChunk> currentDocuments = new LinkedHashMap<>();
        versioned.forEach(item -> currentDocuments.putIfAbsent(item.documentId(), item));
        Set<String> unchanged = new HashSet<>();
        Set<String> changed = new HashSet<>();
        int added = 0;
        int modified = 0;
        for (var entry : currentDocuments.entrySet()) {
            RagManifestDocument old = previous.get(entry.getKey());
            VersionedKnowledgeChunk current = entry.getValue();
            if (old == null) {
                added++;
                changed.add(entry.getKey());
            } else if (sameVersionedContent(old, current)) {
                unchanged.add(entry.getKey());
            } else {
                modified++;
                changed.add(entry.getKey());
            }
        }
        int deleted = (int) previous.keySet().stream().filter(id -> !currentDocuments.containsKey(id)).count();
        if (added == 0 && modified == 0 && deleted == 0) {
            return summary(loaded.documents().size(), chunks.size(), 0, 0, 0, 0, versions);
        }

        String targetIndex;
        try {
            targetIndex = indexManager.ensureIndex(versions);
            failures += incrementalStore.copyUnchanged(properties.getIndexAlias(), targetIndex, unchanged, versions);
        } catch (RuntimeException exception) {
            return summary(loaded.documents().size(), chunks.size(), added, modified, deleted,
                    Math.max(1, failures), versions);
        }
        List<VersionedKnowledgeChunk> changedChunks = versioned.stream()
                .filter(item -> changed.contains(item.documentId())).toList();
        failures += embedAndWrite(targetIndex, changedChunks);
        if (failures == 0) {
            indexManager.switchReadAlias(targetIndex);
        }
        return summary(loaded.documents().size(), chunks.size(), added, modified, deleted, failures, versions);
    }

    private RagIncrementalIndexSummary fullFallback(int documentCount, List<VersionedKnowledgeChunk> chunks,
                                                     RagVersionSet versions) {
        int failures = 0;
        String targetIndex;
        try {
            targetIndex = indexManager.ensureIndex(versions);
        } catch (RuntimeException exception) {
            return summary(documentCount, chunks.size(), documentCount, 0, 0, chunks.size(), versions);
        }
        failures += embedAndWrite(targetIndex, chunks);
        if (failures == 0) {
            indexManager.switchReadAlias(targetIndex);
        }
        return summary(documentCount, chunks.size(), documentCount, 0, 0, failures, versions);
    }

    private int embedAndWrite(String indexName, List<VersionedKnowledgeChunk> chunks) {
        int failures = 0;
        for (int start = 0; start < chunks.size(); start += properties.getEmbeddingBatchSize()) {
            int end = Math.min(start + properties.getEmbeddingBatchSize(), chunks.size());
            List<VersionedKnowledgeChunk> batch = chunks.subList(start, end);
            try {
                List<TextSegment> segments = batch.stream()
                        .map(item -> TextSegment.from(item.chunk().content())).toList();
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                failures += indexStore.writeBatch(indexName, batch, embeddings);
            } catch (RuntimeException exception) {
                failures += batch.size();
            }
        }
        return failures;
    }

    private boolean sameVersionedContent(RagManifestDocument old, VersionedKnowledgeChunk current) {
        return old.contentHash().equals(current.chunk().source().contentHash())
                && old.embeddingModelVersion().equals(current.versions().embeddingModelVersion())
                && old.chunkPolicyVersion().equals(current.versions().chunkPolicyVersion());
    }

    private RagIncrementalIndexSummary summary(int documents, int chunks, int added, int modified,
                                               int deleted, int failures, RagVersionSet versions) {
        return new RagIncrementalIndexSummary(documents, chunks, added, modified, deleted,
                failures, versions.ragIndexVersion());
    }
}
