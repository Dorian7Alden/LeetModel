package com.leetmodel.knowledge.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ElasticsearchConfiguration {
    @Bean(destroyMethod = "close")
    RestClient knowledgeElasticsearchClient(KnowledgeRetrievalProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build();
    }
}
