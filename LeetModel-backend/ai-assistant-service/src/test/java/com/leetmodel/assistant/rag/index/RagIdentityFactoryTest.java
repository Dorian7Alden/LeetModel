package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.chunk.KnowledgeChunk;
import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.source.KnowledgeDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagIdentityFactoryTest {

    private final RagIdentityFactory factory = new RagIdentityFactory(new RagProperties());

    @Test
    void repeatedIndexingProducesIdenticalIdsAndVersions() {
        List<KnowledgeChunk> chunks = List.of(chunk("数学建模/模型/A.md", "hash-a", 0, "片段一"),
                chunk("数学建模/模型/A.md", "hash-a", 1, "片段二"));

        List<VersionedKnowledgeChunk> first = factory.version(chunks);
        List<VersionedKnowledgeChunk> second = factory.version(chunks);

        assertThat(second).isEqualTo(first);
        assertThat(first).extracting(VersionedKnowledgeChunk::chunkId).doesNotHaveDuplicates();
    }

    @Test
    void changingOneDocumentOnlyChangesThatDocumentsChunkIds() {
        List<VersionedKnowledgeChunk> before = factory.version(List.of(
                chunk("数学建模/A.md", "hash-a1", 0, "A"),
                chunk("数学建模/B.md", "hash-b1", 0, "B")));
        List<VersionedKnowledgeChunk> after = factory.version(List.of(
                chunk("数学建模/A.md", "hash-a2", 0, "A changed"),
                chunk("数学建模/B.md", "hash-b1", 0, "B")));

        assertThat(after.get(0).documentId()).isEqualTo(before.get(0).documentId());
        assertThat(after.get(0).chunkId()).isNotEqualTo(before.get(0).chunkId());
        assertThat(after.get(1).documentId()).isEqualTo(before.get(1).documentId());
        assertThat(after.get(1).chunkId()).isEqualTo(before.get(1).chunkId());
        assertThat(after.get(0).versions().contentVersion())
                .isNotEqualTo(before.get(0).versions().contentVersion());
    }

    @Test
    void exposesAllVersionFields() {
        RagVersionSet versions = factory.versions(List.of(chunk("数学建模/A.md", "hash", 0, "A")));

        assertThat(versions.contentVersion()).matches("content-[0-9a-f]{16}");
        assertThat(versions.embeddingModelVersion()).isEqualTo("qwen3.7-text-embedding@1024");
        assertThat(versions.chunkPolicyVersion()).isEqualTo("zh-structure-v1-80-320-480-48");
        assertThat(versions.ragIndexVersion()).matches("rag-v1-[0-9a-f]{16}");
    }

    private KnowledgeChunk chunk(String path, String hash, int ordinal, String content) {
        KnowledgeDocument document = new KnowledgeDocument(path, "标题", List.of("标签"), "摘要",
                List.of("数学建模"), hash, Instant.EPOCH, content.length(), content);
        return new KnowledgeChunk(document, ordinal, content, content.length());
    }
}
