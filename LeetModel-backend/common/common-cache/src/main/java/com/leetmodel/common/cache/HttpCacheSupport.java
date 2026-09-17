package com.leetmodel.common.cache;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

/**
 * 生成公开 GET 响应的 ETag 和 Cache-Control。
 */
public final class HttpCacheSupport {

    private HttpCacheSupport() {
    }

    /**
     * 构建当前 HTTP 缓存验证器。
     *
     * @param version 区域版本
     * @param schemaVersion 响应结构版本
     * @param logicalKey 响应逻辑 Key
     * @param maxAge 公开新鲜期
     * @return HTTP 缓存验证器
     */
    public static Validator validator(
            CacheVersionView version,
            String schemaVersion,
            String logicalKey,
            Duration maxAge
    ) {
        String source = version.token() + ":" + schemaVersion + ":" + logicalKey;
        String digest = CacheKeyHasher.sha256(source).substring(0, 16);
        String etag = "W/\"" + version.generation() + "-r" + version.revision()
                + "-" + schemaVersion + "-" + digest + "\"";
        Duration effectiveMaxAge = version.degraded() && maxAge.compareTo(Duration.ofSeconds(5)) > 0
                ? Duration.ofSeconds(5) : maxAge;
        return new Validator(etag, effectiveMaxAge);
    }

    /**
     * 一次 HTTP 条件缓存判定。
     *
     * @param etag 弱 ETag
     * @param maxAge 公开新鲜期
     */
    public record Validator(String etag, Duration maxAge) {

        /**
         * 判断请求中的 If-None-Match 是否包含当前 ETag。
         *
         * @param ifNoneMatch If-None-Match 请求头
         * @return 是否未变更
         */
        public boolean matches(String ifNoneMatch) {
            if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;
            if ("*".equals(ifNoneMatch.trim())) return true;
            for (String candidate : ifNoneMatch.split(",")) {
                if (etag.equals(candidate.trim())) return true;
            }
            return false;
        }

        /**
         * 返回不携带响应体的 304。
         *
         * @param <T> 响应体类型
         * @return 304 响应
         */
        public <T> ResponseEntity<T> notModified() {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(headers())
                    .build();
        }

        /**
         * 返回携带当前缓存头的 200。
         *
         * @param body 响应体
         * @param <T> 响应体类型
         * @return 200 响应
         */
        public <T> ResponseEntity<T> ok(T body) {
            return ResponseEntity.ok()
                    .headers(headers())
                    .body(body);
        }

        /**
         * 组装 ETag 与公开缓存头。
         *
         * @return HTTP 响应头
         */
        private HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            headers.setETag(etag);
            headers.setCacheControl(CacheControl.maxAge(maxAge).cachePublic());
            return headers;
        }
    }
}
