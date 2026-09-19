package com.leetmodel.problem.search;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 题库全文检索客户端配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProblemSearchProperties.class)
@ConditionalOnProperty(prefix = "problem.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProblemSearchConfiguration {

    /**
     * 创建题库检索专用 Elasticsearch 低层客户端。
     *
     * @param properties 检索配置
     * @return REST 客户端
     */
    @Bean(destroyMethod = "close")
    RestClient problemSearchElasticsearchClient(ProblemSearchProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getElasticsearchUri())).build();
    }
}
