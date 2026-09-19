package com.leetmodel.common.core.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBlacklistPolicyTest {

    private static final String PREFIX = "leetmodel:auth:blacklist:";

    @Test
    @DisplayName("黑名单键使用 Token 指纹且不包含 Token 原文")
    void keyUsesFingerprintInsteadOfRawToken() {
        String token = "header.payload.signature";

        String key = TokenBlacklistPolicy.key(PREFIX, token);

        assertThat(key).startsWith(PREFIX);
        assertThat(key).doesNotContain(token);
        assertThat(key).isEqualTo(PREFIX + TokenFingerprint.of(token));
    }

    @Test
    @DisplayName("相同 Token 得到相同指纹，不同 Token 得到不同指纹")
    void fingerprintIsStableAndDistinct() {
        assertThat(TokenFingerprint.of("token-a")).isEqualTo(TokenFingerprint.of("token-a"));
        assertThat(TokenFingerprint.of("token-a")).isNotEqualTo(TokenFingerprint.of("token-b"));
        assertThat(TokenFingerprint.of("token-a")).hasSize(64);
    }

    @Test
    @DisplayName("空 Token 不能生成指纹")
    void blankTokenIsRejected() {
        assertThatThrownBy(() -> TokenFingerprint.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TTL 取剩余有效期与上限中的较小值")
    void ttlUsesSmallerValueBetweenRemainingAndLimit() {
        assertThat(TokenBlacklistPolicy.ttlSeconds(600, 604800)).isEqualTo(600);
        assertThat(TokenBlacklistPolicy.ttlSeconds(604800, 3600)).isEqualTo(3600);
    }

    @Test
    @DisplayName("剩余有效期不可解析时使用兜底 TTL，不写入永久记录")
    void ttlFallsBackWhenRemainingIsUnavailable() {
        assertThat(TokenBlacklistPolicy.ttlSeconds(-1, 604800)).isEqualTo(1);
        assertThat(TokenBlacklistPolicy.ttlSeconds(0, 604800)).isEqualTo(1);
        assertThat(TokenBlacklistPolicy.ttlSeconds(600, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("日志前缀只暴露指纹前若干位")
    void logPrefixDoesNotExposeFullFingerprint() {
        String fingerprint = TokenFingerprint.of("token-a");

        assertThat(TokenFingerprint.logPrefix(fingerprint)).hasSize(12);
        assertThat(fingerprint).startsWith(TokenFingerprint.logPrefix(fingerprint));
        assertThat(TokenFingerprint.logPrefix(null)).isEqualTo("unknown");
    }
}
