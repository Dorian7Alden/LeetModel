package com.leetmodel.common.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Token 指纹工具。
 *
 * <p>黑名单键、日志与指标只使用 Token 的 SHA-256 指纹，避免 Token 原文出现在存储、
 * 日志和监控系统中；指纹本身不可逆，不同 Token 生成不同指纹。</p>
 */
public final class TokenFingerprint {

    /** 日志中展示的指纹前缀长度，足够区分不同请求又不足以还原完整指纹。 */
    private static final int LOG_PREFIX_LENGTH = 12;

    private TokenFingerprint() {
        // 工具类禁止实例化
    }

    /**
     * 计算 Token 的 SHA-256 指纹。
     *
     * @param token Token 原文，不能为空
     * @return 小写十六进制指纹
     */
    public static String of(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        byte[] digest = sha256(token);
        return HexFormat.of().formatHex(digest);
    }

    /**
     * 生成供日志使用的短前缀。
     *
     * @param fingerprint Token 指纹
     * @return 指纹前若干位；输入不可用时返回固定占位符
     */
    public static String logPrefix(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < LOG_PREFIX_LENGTH) {
            return "unknown";
        }
        return fingerprint.substring(0, LOG_PREFIX_LENGTH);
    }

    /**
     * 计算 SHA-256 摘要。
     *
     * @param token Token 原文
     * @return 摘要字节
     */
    private static byte[] sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
