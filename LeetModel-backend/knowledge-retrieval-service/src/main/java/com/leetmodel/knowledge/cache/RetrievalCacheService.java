package com.leetmodel.knowledge.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识检索两级缓存服务（L1 精确结果缓存 + L2 任务向量语义缓存）。
 * 优先使用 Redis，在连接异常或离线单测模式下自动降级为线程安全内存缓存。
 */
@Slf4j
@Service
public class RetrievalCacheService {

    private final RetrievalCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    // 内存兜底缓存
    private final Map<String, CachedL1Item> memoryL1Cache = new ConcurrentHashMap<>();
    private final Map<String, List<L2SemanticEntry>> memoryL2Cache = new ConcurrentHashMap<>();

    public RetrievalCacheService(RetrievalCacheProperties properties,
                                 ObjectMapper objectMapper,
                                 @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询 L1 精确结果缓存。
     */
    public List<KnowledgeCitationDTO> getL1Exact(String workflow, String category,
                                                String query, int topK, String manifestVersion) {
        if (!properties.isEnabled()) return null;
        String key = buildL1Key(workflow, category, query, topK, manifestVersion);

        // 1. 尝试 Redis 读取
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    log.debug("L1 缓存命中 (Redis): key={}", key);
                    return objectMapper.readValue(json, new TypeReference<List<KnowledgeCitationDTO>>() {});
                }
            } catch (Exception e) {
                log.warn("读取 Redis L1 缓存异常，回退内存: {}", e.getMessage());
            }
        }

        // 2. 内存回退读取
        CachedL1Item item = memoryL1Cache.get(key);
        if (item != null && !item.isExpired()) {
            log.debug("L1 缓存命中 (Memory): key={}", key);
            return item.citations();
        }
        return null;
    }

    /**
     * 写入 L1 精确结果缓存。
     */
    public void putL1Exact(String workflow, String category, String query,
                           int topK, String manifestVersion, List<KnowledgeCitationDTO> citations) {
        if (!properties.isEnabled() || citations == null) return;
        String key = buildL1Key(workflow, category, query, topK, manifestVersion);
        Duration ttl = citations.isEmpty() ? Duration.ofMinutes(3) : properties.getL1Ttl();

        // 1. 尝试 Redis 写入
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(citations);
                redisTemplate.opsForValue().set(key, json, ttl);
            } catch (Exception e) {
                log.warn("写入 Redis L1 缓存异常: {}", e.getMessage());
            }
        }

        // 2. 同步内存备份
        long expireAt = System.currentTimeMillis() + ttl.toMillis();
        memoryL1Cache.put(key, new CachedL1Item(citations, expireAt));
    }

    /**
     * 查询 L2 任务语义向量缓存（余弦相似度 >= semanticThreshold）。
     */
    public List<KnowledgeCitationDTO> getL2Semantic(String workflow, String category,
                                                   String query, List<Float> queryVector) {
        if (!properties.isEnabled() || queryVector == null || queryVector.isEmpty()) return null;
        String catKey = category != null ? category : "ALL";

        // 遍历分类下的历史语义任务
        List<L2SemanticEntry> entries = memoryL2Cache.getOrDefault(catKey, Collections.emptyList());
        L2SemanticEntry bestMatch = null;
        double bestSimilarity = -1.0;

        for (L2SemanticEntry entry : entries) {
            double sim = cosineSimilarity(queryVector, entry.embedding());
            if (sim >= properties.getSemanticThreshold() && sim > bestSimilarity) {
                bestSimilarity = sim;
                bestMatch = entry;
            }
        }

        if (bestMatch != null) {
            log.info("L2 任务语义缓存命中: query='{}', matchQuery='{}', similarity={}",
                    query, bestMatch.query(), String.format("%.4f", bestSimilarity));
            return bestMatch.citations();
        }
        return null;
    }

    /**
     * 写入 L2 任务语义向量缓存。
     */
    public void putL2Semantic(String workflow, String category, String query,
                              List<Float> queryVector, List<KnowledgeCitationDTO> citations) {
        if (!properties.isEnabled() || queryVector == null || citations == null || citations.isEmpty()) return;
        String catKey = category != null ? category : "ALL";

        L2SemanticEntry newEntry = new L2SemanticEntry(
                UUID.randomUUID().toString(),
                query,
                catKey,
                List.copyOf(queryVector),
                List.copyOf(citations),
                System.currentTimeMillis()
        );

        memoryL2Cache.compute(catKey, (k, list) -> {
            List<L2SemanticEntry> nextList = list != null ? new ArrayList<>(list) : new ArrayList<>();
            // 限制单分类下历史向量条目数，保留最新 50 条
            if (nextList.size() >= 50) {
                nextList.remove(0);
            }
            nextList.add(newEntry);
            return nextList;
        });
        log.debug("成功写入 L2 任务语义缓存: category={}, query='{}'", catKey, query);
    }

    /**
     * 按分类失效缓存。
     */
    public void invalidateCategory(String category) {
        if (category == null) return;
        memoryL2Cache.remove(category);
        memoryL1Cache.keySet().removeIf(k -> k.contains(":" + category + ":"));
        if (redisTemplate != null) {
            try {
                java.util.Set<String> keys = redisTemplate.keys("lm:kr:l1:*:" + category + ":*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Redis 清理分类缓存异常: {}", e.getMessage());
            }
        }
        log.info("按分类淘汰缓存: category={}", category);
    }

    /**
     * 全量失效缓存。
     */
    public void invalidateAll() {
        memoryL1Cache.clear();
        memoryL2Cache.clear();
        log.info("全量清空两级检索缓存");
    }

    public static double cosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size() || v1.isEmpty()) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            float a = v1.get(i);
            float b = v2.get(i);
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA <= 0.0 || normB <= 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String buildL1Key(String workflow, String category, String query,
                             int topK, String manifestVersion) {
        String cat = (category != null && !category.isBlank()) ? category : "ALL";
        String raw = (workflow != null ? workflow : "") + ":"
                + cat + ":"
                + (query != null ? query : "") + ":" + topK;
        String versionNs = manifestVersion != null ? manifestVersion : "DEFAULT";
        return "lm:kr:l1:" + versionNs + ":" + cat + ":" + md5(raw);
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private record CachedL1Item(List<KnowledgeCitationDTO> citations, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
