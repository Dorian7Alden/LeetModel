package com.leetmodel.assistant.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用确定性 chunkId 批量写入 Elasticsearch。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "store-type", havingValue = "ELASTICSEARCH",
        matchIfMissing = true)
public class ElasticsearchRagIndexStore implements RagIndexStore {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchRagIndexStore(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public int writeBatch(String indexName, List<VersionedKnowledgeChunk> chunks,
                          List<Embedding> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("片段与向量数量不一致");
        }
        StringBuilder body = new StringBuilder();
        try {
            for (int index = 0; index < chunks.size(); index++) {
                VersionedKnowledgeChunk item = chunks.get(index);
                body.append(objectMapper.writeValueAsString(Map.of(
                        "index", Map.of("_index", indexName, "_id", item.chunkId())))).append('\n');
                body.append(objectMapper.writeValueAsString(document(item, embeddings.get(index)))).append('\n');
            }
            Request request = new Request("POST", "/_bulk?refresh=wait_for");
            request.setEntity(new NStringEntity(body.toString(),
                    ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
            Response response = client.performRequest(request);
            JsonNode json = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            if (!json.path("errors").asBoolean(false)) {
                return 0;
            }
            int failures = 0;
            for (JsonNode item : json.path("items")) {
                if (item.path("index").path("status").asInt() >= 300) {
                    failures++;
                }
            }
            return failures;
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch RAG 批量写入失败", exception);
        }
    }

    private Map<String, Object> document(VersionedKnowledgeChunk item, Embedding embedding) {
        var source = item.chunk().source();
        List<Float> vector = new ArrayList<>(embedding.vector().length);
        for (float value : embedding.vector()) {
            vector.add(value);
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("documentId", item.documentId());
        document.put("chunkId", item.chunkId());
        document.put("content", item.chunk().content());
        document.put("embedding", vector);
        document.put("sourcePath", source.relativePath());
        document.put("title", source.title());
        document.put("tags", source.tags());
        document.put("hierarchy", source.hierarchy());
        document.put("contentHash", source.contentHash());
        document.put("ordinal", item.chunk().ordinal());
        document.put("estimatedTokens", item.chunk().estimatedTokens());
        document.put("lastModifiedAt", source.lastModifiedAt().toString());
        document.put("byteSize", source.byteSize());
        document.put("contentVersion", item.versions().contentVersion());
        document.put("embeddingModelVersion", item.versions().embeddingModelVersion());
        document.put("chunkPolicyVersion", item.versions().chunkPolicyVersion());
        document.put("ragIndexVersion", item.versions().ragIndexVersion());
        return document;
    }
}
