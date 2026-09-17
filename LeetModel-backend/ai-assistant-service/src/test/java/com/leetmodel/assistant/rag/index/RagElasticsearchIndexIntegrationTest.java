package com.leetmodel.assistant.rag.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.config.RagProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RAG_ES_INTEGRATION", matches = "true")
class RagElasticsearchIndexIntegrationTest {

    @Test
    void createsIndependentVectorIndicesAndRejectsDimensionMismatch() throws Exception {
        RagProperties properties = properties(1024);
        try (RestClient client = RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build()) {
            RagElasticsearchIndexManager manager = new RagElasticsearchIndexManager(
                    client, new ObjectMapper(), properties);
            RagVersionSet oldVersion = versions("rag-v1-s4-09-old");
            RagVersionSet currentVersion = versions("rag-v1-s4-09-current");

            String oldIndex = manager.ensureIndex(oldVersion);
            String currentIndex = manager.ensureIndex(currentVersion);
            assertThat(manager.ensureIndex(currentVersion)).isEqualTo(currentIndex);
            assertThat(exists(client, oldIndex)).isTrue();
            assertThat(exists(client, currentIndex)).isTrue();
            assertThat(oldIndex).isNotEqualTo(currentIndex);
            manager.switchReadAlias(currentIndex);
            assertThat(aliasPointsTo(client, properties.getIndexAlias(), currentIndex)).isTrue();

            RagElasticsearchIndexManager wrongDimension = new RagElasticsearchIndexManager(
                    client, new ObjectMapper(), properties(3));
            assertThatThrownBy(() -> wrongDimension.ensureIndex(currentVersion))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("向量维度不匹配");
        }
    }

    private RagProperties properties(int dimension) {
        RagProperties properties = new RagProperties();
        properties.setIndexAlias("leetmodel-rag-s4-09-test-read");
        properties.setEmbeddingDimension(dimension);
        return properties;
    }

    private RagVersionSet versions(String ragIndexVersion) {
        return new RagVersionSet("content-test", "embedding-test", "chunk-test", ragIndexVersion);
    }

    private boolean exists(RestClient client, String indexName) throws Exception {
        Response response = client.performRequest(new Request("HEAD", "/" + indexName));
        return response.getStatusLine().getStatusCode() == 200;
    }

    private boolean aliasPointsTo(RestClient client, String alias, String indexName) throws Exception {
        Response response = client.performRequest(new Request("GET", "/_alias/" + alias));
        return new ObjectMapper().readTree(response.getEntity().getContent()).has(indexName);
    }
}
