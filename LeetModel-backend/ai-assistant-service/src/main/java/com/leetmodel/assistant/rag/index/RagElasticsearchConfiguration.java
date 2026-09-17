package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.config.RagProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 正式 Elasticsearch Store 的低层客户端配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "assistant.rag", name = "store-type", havingValue = "ELASTICSEARCH",
        matchIfMissing = true)
public class RagElasticsearchConfiguration {

    @Bean(destroyMethod = "close")
    RestClient ragElasticsearchRestClient(RagProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build();
    }
}
