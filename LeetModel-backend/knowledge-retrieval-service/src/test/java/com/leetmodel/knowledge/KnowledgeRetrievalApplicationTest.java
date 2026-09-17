package com.leetmodel.knowledge;

import com.leetmodel.knowledge.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.cloud.nacos.discovery.enabled=false"
)
class KnowledgeRetrievalApplicationTest {
    @Autowired
    private KnowledgeRetrievalService service;

    @Test
    void startsWithoutAServiceOwnedDatasource() {
        assertThat(service).isNotNull();
    }
}
