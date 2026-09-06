package com.leetmodel.knowledge.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 检索缓存配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.retrieval.cache")
public class RetrievalCacheProperties {

    private boolean enabled = true;

    /** L1 结果缓存 TTL，默认 2 小时 */
    private Duration l1Ttl = Duration.ofHours(2);

    /** L2 任务语义向量缓存 TTL，默认 24 小时 */
    private Duration l2Ttl = Duration.ofHours(24);

    /** L2 语义余弦相似度命中阈值，默认 0.95 */
    private double semanticThreshold = 0.95;
}
