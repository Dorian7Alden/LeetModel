package com.leetmodel.common.cache;

/**
 * 向 HTTP 层暴露的当前缓存版本。
 *
 * @param generation 缓存代际
 * @param revision 区域版本
 * @param degraded 是否处于 Redis 降级状态
 */
public record CacheVersionView(String generation, long revision, boolean degraded) {

    /**
     * 返回稳定版本串。
     *
     * @return 稳定版本串
     */
    public String token() {
        return generation + "-r" + revision;
    }
}
