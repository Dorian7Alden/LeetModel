package com.leetmodel.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 为已标准化查询生成不含敏感内容的稳定 Key。
 */
public final class CacheKeyHasher {

    private CacheKeyHasher() {
    }

    /**
     * 生成 SHA-256 十六进制摘要。
     *
     * @param value 已标准化内容
     * @return SHA-256 摘要
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
