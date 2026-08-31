package com.leetmodel.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.retrieval")
public class KnowledgeRetrievalProperties {
    private String knowledgeBasePath = "../rag_kb";
    private String indexAlias = "leetmodel-rag-v1-read";
    private String elasticsearchUri = "http://127.0.0.1:9200";
    private String embeddingModelVersion = "qwen3.7-text-embedding@1024";
    private int embeddingDimension = 1024;
    private int topK = 8;
    private int tokenBudget = 3000;
    private double scoreThreshold = 0.65;
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int directoryCandidateLimit = 160;
    private int directorySelectionLimit = 8;
}
