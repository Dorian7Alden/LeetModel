package com.leetmodel.assistant.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.config.RagProperties;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 创建并校验 RAG 物理向量索引；不会覆盖或删除任何已有索引。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "store-type", havingValue = "ELASTICSEARCH",
        matchIfMissing = true)
public class RagElasticsearchIndexManager {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public RagElasticsearchIndexManager(RestClient client, ObjectMapper objectMapper, RagProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String ensureIndex(RagVersionSet versions) {
        String indexName = physicalIndexName(versions.ragIndexVersion());
        try {
            if (!exists(indexName)) {
                create(indexName);
            }
            validateDimension(indexName);
            return indexName;
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch RAG 索引校验失败: " + indexName, exception);
        }
    }

    public String physicalIndexName(String ragIndexVersion) {
        String version = ragIndexVersion == null ? "" : ragIndexVersion.toLowerCase();
        if (!version.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new IllegalArgumentException("ragIndexVersion 不能用于 Elasticsearch 索引名");
        }
        String base = properties.getIndexAlias().replaceFirst("-read$", "");
        return base + "-" + version;
    }

    /** 全部片段写入成功后，以单次 Elasticsearch aliases 请求切换读别名。 */
    public void switchReadAlias(String indexName) {
        String expectedPrefix = properties.getIndexAlias().replaceFirst("-read$", "") + "-";
        if (indexName == null || !indexName.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("读别名只能指向受管 RAG 物理索引");
        }
        Map<String, Object> remove = Map.of("remove", Map.of(
                "index", "*", "alias", properties.getIndexAlias(), "must_exist", false));
        Map<String, Object> add = Map.of("add", Map.of(
                "index", indexName, "alias", properties.getIndexAlias(), "is_write_index", false));
        Request request = new Request("POST", "/_aliases");
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(Map.of("actions", List.of(remove, add))));
            client.performRequest(request);
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch RAG 读别名切换失败", exception);
        }
    }

    private boolean exists(String indexName) throws IOException {
        Response response;
        try {
            response = client.performRequest(new Request("HEAD", "/" + indexName));
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw exception;
        }
        return response.getStatusLine().getStatusCode() == 200;
    }

    private void create(String indexName) throws IOException {
        Map<String, Object> vector = Map.of(
                "type", "dense_vector",
                "dims", properties.getEmbeddingDimension(),
                "index", true,
                "similarity", "cosine");
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("documentId", keyword()), Map.entry("chunkId", keyword()),
                Map.entry("content", Map.of("type", "text", "index", false)),
                Map.entry("embedding", vector), Map.entry("sourcePath", keyword()),
                Map.entry("title", Map.of("type", "text")), Map.entry("tags", keyword()),
                Map.entry("hierarchy", keyword()), Map.entry("contentHash", keyword()),
                Map.entry("ordinal", Map.of("type", "integer")),
                Map.entry("estimatedTokens", Map.of("type", "integer")),
                Map.entry("lastModifiedAt", Map.of("type", "date")),
                Map.entry("byteSize", Map.of("type", "long")),
                Map.entry("contentVersion", keyword()), Map.entry("embeddingModelVersion", keyword()),
                Map.entry("chunkPolicyVersion", keyword()), Map.entry("ragIndexVersion", keyword()));
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(objectMapper.writeValueAsString(Map.of("mappings", Map.of("properties", fields))));
        client.performRequest(request);
    }

    private void validateDimension(String indexName) throws IOException {
        Response response = client.performRequest(new Request("GET", "/" + indexName + "/_mapping"));
        JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
        JsonNode embedding = root.path(indexName).path("mappings").path("properties").path("embedding");
        int actualDimension = embedding.path("dims").asInt(-1);
        String actualType = embedding.path("type").asText();
        if (!"dense_vector".equals(actualType) || actualDimension != properties.getEmbeddingDimension()) {
            throw new IllegalStateException("RAG 索引向量维度不匹配: expected="
                    + properties.getEmbeddingDimension() + ", actual=" + actualDimension);
        }
    }

    private Map<String, Object> keyword() {
        return Map.of("type", "keyword");
    }
}
