package com.leetmodel.ranking.cache;

import com.leetmodel.common.cache.CacheKeyHasher;
import com.leetmodel.common.cache.CacheVersionProvider;
import com.leetmodel.common.cache.HttpCacheSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 当前题目排行的缓存区域和 HTTP 契约。
 */
@Component
@RequiredArgsConstructor
public class RankingCachePolicy {

    public static final String REGION = "current";
    public static final String SCHEMA_VERSION = "v1";

    private final CacheVersionProvider versionProvider;

    /**
     * 返回指定题目的失效作用域。
     *
     * @param problemId 题目 ID
     * @return 作用域
     */
    public static String scope(Long problemId) {
        return "problem-" + problemId;
    }

    /**
     * 创建当前排行 HTTP 验证器。
     *
     * @param problemId 题目 ID
     * @param keyword 可选队伍关键词
     * @return HTTP 验证器
     */
    public HttpCacheSupport.Validator currentValidator(Long problemId, String keyword) {
        String normalized = keyword == null || keyword.isBlank()
                ? "_" : keyword.trim().toLowerCase(Locale.ROOT);
        String logicalKey = "overview:" + CacheKeyHasher.sha256(normalized);
        return validator(problemId, logicalKey);
    }

    /**
     * 创建队伍定位 HTTP 验证器。
     *
     * @param problemId 题目 ID
     * @param teamId 队伍 ID
     * @param radius 附近行数
     * @return HTTP 验证器
     */
    public HttpCacheSupport.Validator locateValidator(Long problemId, Long teamId, int radius) {
        return validator(problemId, "locate:" + teamId + ":" + radius);
    }

    /**
     * 创建题目排行 HTTP 验证器。
     *
     * @param problemId 题目 ID
     * @param logicalKey 响应逻辑 Key
     * @return HTTP 验证器
     */
    private HttpCacheSupport.Validator validator(Long problemId, String logicalKey) {
        String scope = scope(problemId);
        return HttpCacheSupport.validator(
                versionProvider.current(REGION, scope),
                SCHEMA_VERSION,
                logicalKey,
                Duration.ofSeconds(10)
        );
    }
}
