package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.chunk.KnowledgeChunk;
import com.leetmodel.assistant.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 生成路径稳定的文档 ID、内容感知的片段 ID 和确定性索引版本。 */
@Component
public class RagIdentityFactory {

    private static final String SCHEMA_VERSION = "rag-es-v1";
    private static final String CHUNK_ALGORITHM_VERSION = "zh-structure-v1";

    private final RagProperties properties;

    public RagIdentityFactory(RagProperties properties) {
        this.properties = properties;
    }

    public List<VersionedKnowledgeChunk> version(List<KnowledgeChunk> chunks) {
        RagVersionSet versions = versions(chunks);
        return chunks.stream().map(chunk -> {
            String documentId = digest("document\0" + chunk.source().relativePath());
            String chunkId = digest("chunk\0" + documentId + "\0"
                    + chunk.source().contentHash() + "\0" + chunk.ordinal());
            return new VersionedKnowledgeChunk(documentId, chunkId, chunk, versions);
        }).toList();
    }

    public RagVersionSet versions(List<KnowledgeChunk> chunks) {
        Map<String, String> documents = new LinkedHashMap<>();
        chunks.stream().sorted(Comparator.comparing(chunk -> chunk.source().relativePath()))
                .forEach(chunk -> documents.put(chunk.source().relativePath(), chunk.source().contentHash()));
        String contentMaterial = documents.entrySet().stream()
                .map(entry -> entry.getKey() + "\0" + entry.getValue())
                .reduce("", (left, right) -> left + right + "\n");
        String contentVersion = "content-" + shortDigest(contentMaterial);
        String chunkPolicyVersion = CHUNK_ALGORITHM_VERSION + "-"
                + properties.getChunkMinTokens() + "-" + properties.getChunkTargetTokens() + "-"
                + properties.getChunkMaxTokens() + "-" + properties.getChunkOverlapTokens();
        String embeddingModelVersion = properties.getEmbeddingModelVersion();
        String ragIndexVersion = "rag-v1-" + shortDigest(SCHEMA_VERSION + "\0" + contentVersion + "\0"
                + embeddingModelVersion + "\0" + chunkPolicyVersion);
        return new RagVersionSet(contentVersion, embeddingModelVersion, chunkPolicyVersion, ragIndexVersion);
    }

    private String shortDigest(String value) {
        return digest(value).substring(0, 16);
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支持 SHA-256", exception);
        }
    }
}
