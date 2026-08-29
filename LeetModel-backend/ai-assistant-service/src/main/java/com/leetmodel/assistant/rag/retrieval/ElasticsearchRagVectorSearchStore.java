package com.leetmodel.assistant.rag.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.config.RagProperties;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Elasticsearch cosine dense_vector 基础召回。 */
@Component
@ConditionalOnBean(RestClient.class)
public class ElasticsearchRagVectorSearchStore implements RagVectorSearchStore {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public ElasticsearchRagVectorSearchStore(RestClient client, ObjectMapper objectMapper,
                                              RagProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<RagVectorHit> search(List<Float> queryVector, int topK) {
        return searchIndex(queryVector, topK, properties.getIndexAlias());
    }

    @Override
    public List<RagVectorHit> search(List<Float> queryVector, int topK, String ragIndexVersion) {
        String version = ragIndexVersion == null ? "" : ragIndexVersion.toLowerCase();
        if (!version.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new RagStoreException("RAG 索引版本非法", false, null);
        }
        String base = properties.getIndexAlias().replaceFirst("-read$", "");
        return searchIndex(queryVector, topK, base + "-" + version);
    }

    private List<RagVectorHit> searchIndex(List<Float> queryVector, int topK, String indexName) {
        Map<String, Object> knn = Map.of(
                "field", "embedding", "query_vector", queryVector,
                "k", topK, "num_candidates", Math.max(100, topK * 10));
        Map<String, Object> body = Map.of(
                "size", topK,
                "_source", List.of("chunkId", "documentId", "content", "sourcePath", "title",
                        "ragIndexVersion", "estimatedTokens"),
                "knn", knn);
        Request request = new Request("POST", "/" + indexName + "/_search");
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            int timeoutMillis = Math.toIntExact(properties.getRequestTimeout().toMillis());
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(timeoutMillis).setSocketTimeout(timeoutMillis)
                    .setConnectionRequestTimeout(timeoutMillis).build();
            request.setOptions(RequestOptions.DEFAULT.toBuilder().setRequestConfig(requestConfig));
            Response response = client.performRequest(request);
            JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
            List<RagVectorHit> hits = new ArrayList<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                hits.add(new RagVectorHit(
                        source.path("chunkId").asText(), source.path("documentId").asText(),
                        source.path("content").asText(), hit.path("_score").asDouble(),
                        source.path("sourcePath").asText(), source.path("title").asText(),
                        source.path("ragIndexVersion").asText(),
                        source.path("estimatedTokens").asInt()));
            }
            return List.copyOf(hits);
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return List.of();
            }
            throw storeFailure(exception);
        } catch (IOException exception) {
            throw storeFailure(exception);
        }
    }

    private RagStoreException storeFailure(Exception exception) {
        boolean timeout = exception instanceof SocketTimeoutException
                || exception instanceof ConnectTimeoutException
                || exception.getCause() instanceof SocketTimeoutException;
        return new RagStoreException(timeout ? "Elasticsearch 检索超时" : "Elasticsearch 检索失败",
                timeout, exception);
    }
}
