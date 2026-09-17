package com.leetmodel.assistant.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 基于 Elasticsearch _search 与 _reindex 实现快照式增量更新。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "store-type", havingValue = "ELASTICSEARCH",
        matchIfMissing = true)
public class ElasticsearchRagIncrementalStore implements RagIncrementalStore {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public ElasticsearchRagIncrementalStore(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, RagManifestDocument> readManifest(String readAlias) {
        Request request = new Request("POST", "/" + readAlias + "/_search");
        request.setJsonEntity("""
                {"size":10000,"_source":["documentId","sourcePath","contentHash",
                "embeddingModelVersion","chunkPolicyVersion"],"query":{"match_all":{}}}
                """);
        try {
            Response response = client.performRequest(request);
            JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            Map<String, RagManifestDocument> manifest = new LinkedHashMap<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                RagManifestDocument document = new RagManifestDocument(
                        source.path("documentId").asText(), source.path("sourcePath").asText(),
                        source.path("contentHash").asText(), source.path("embeddingModelVersion").asText(),
                        source.path("chunkPolicyVersion").asText());
                manifest.putIfAbsent(document.documentId(), document);
            }
            return Map.copyOf(manifest);
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return Map.of();
            }
            throw new IllegalStateException("Elasticsearch RAG manifest 读取失败", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch RAG manifest 读取失败", exception);
        }
    }

    @Override
    public int copyUnchanged(String readAlias, String targetIndex, Set<String> documentIds,
                             RagVersionSet versions) {
        if (documentIds.isEmpty()) {
            return 0;
        }
        Map<String, Object> scriptParams = Map.of(
                "contentVersion", versions.contentVersion(),
                "embeddingModelVersion", versions.embeddingModelVersion(),
                "chunkPolicyVersion", versions.chunkPolicyVersion(),
                "ragIndexVersion", versions.ragIndexVersion());
        Map<String, Object> script = Map.of(
                "lang", "painless",
                "source", "ctx._source.contentVersion=params.contentVersion;"
                        + "ctx._source.embeddingModelVersion=params.embeddingModelVersion;"
                        + "ctx._source.chunkPolicyVersion=params.chunkPolicyVersion;"
                        + "ctx._source.ragIndexVersion=params.ragIndexVersion",
                "params", scriptParams);
        Map<String, Object> source = Map.of("index", readAlias,
                "query", Map.of("terms", Map.of("documentId", documentIds)));
        Map<String, Object> body = Map.of("source", source, "dest", Map.of("index", targetIndex),
                "script", script);
        Request request = new Request("POST", "/_reindex?wait_for_completion=true&refresh=true");
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            Response response = client.performRequest(request);
            JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            return root.path("failures").size();
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch RAG 未变化片段复制失败", exception);
        }
    }
}
