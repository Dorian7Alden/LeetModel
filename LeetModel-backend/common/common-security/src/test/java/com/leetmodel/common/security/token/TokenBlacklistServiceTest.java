package com.leetmodel.common.security.token;

import cn.dev33.satoken.exception.NotLoginException;
import com.leetmodel.common.core.security.TokenBlacklistPolicy;
import com.leetmodel.common.core.security.TokenBlacklistProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenBlacklistServiceTest {

    private static final String TOKEN = "header.payload.signature";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TokenBlacklistProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private TokenBlacklistService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties = new TokenBlacklistProperties();
        meterRegistry = new SimpleMeterRegistry();
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(meterRegistry);
        service = new TokenBlacklistService(redisTemplate, properties, provider);
    }

    @Test
    @DisplayName("登出写入黑名单：键为指纹、TTL 取剩余有效期")
    void revokeWritesFingerprintKeyWithRemainingTtl() {
        service.revoke(TOKEN, 1200);

        String expectedKey = TokenBlacklistPolicy.key(properties.getKeyPrefix(), TOKEN);
        verify(valueOperations).set(eq(expectedKey), eq("1"), eq(Duration.ofSeconds(1200)));
        assertThat(expectedKey).doesNotContain(TOKEN);
        assertThat(meterRegistry.counter("auth_token_blacklist_revoked_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("TTL 超过上限时按上限写入")
    void revokeClampsTtlByMaxTtlSeconds() {
        properties.setMaxTtlSeconds(3600L);

        service.revoke(TOKEN, 999999L);

        String expectedKey = TokenBlacklistPolicy.key(properties.getKeyPrefix(), TOKEN);
        verify(valueOperations).set(eq(expectedKey), eq("1"), eq(Duration.ofSeconds(3600)));
    }

    @Test
    @DisplayName("关闭开关后不写也不查")
    void disabledSwitchSkipsRedis() {
        properties.setEnabled(false);

        service.revoke(TOKEN, 600);

        assertThat(service.isRevoked(TOKEN)).isFalse();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("命中黑名单时返回 true 并累计命中计数")
    void isRevokedReturnsTrueOnHit() {
        when(redisTemplate.hasKey(TokenBlacklistPolicy.key(properties.getKeyPrefix(), TOKEN)))
                .thenReturn(true);

        boolean revoked = service.isRevoked(TOKEN);

        assertThat(revoked).isTrue();
        assertThat(meterRegistry.counter("auth_token_blacklist_hit_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Redis 异常时默认放行并累计降级计数")
    void redisFailureFailsOpenByDefault() {
        when(redisTemplate.hasKey(any())).thenThrow(new IllegalStateException("redis down"));

        boolean revoked = service.isRevoked(TOKEN);

        assertThat(revoked).isFalse();
        assertThat(meterRegistry.counter("auth_token_blacklist_degraded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("配置 fail-closed 时 Redis 异常按已失效处理")
    void redisFailureFailsClosedWhenConfigured() {
        properties.setFailClosed(true);
        when(redisTemplate.hasKey(any())).thenThrow(new IllegalStateException("redis down"));

        assertThat(service.isRevoked(TOKEN)).isTrue();
    }

    @Test
    @DisplayName("命中黑名单时抛出可区分的未登录异常")
    void rejectIfRevokedThrowsBlacklistedType() {
        when(redisTemplate.hasKey(any())).thenReturn(true);

        assertThatThrownBy(() -> service.rejectIfRevoked(TOKEN))
                .isInstanceOf(NotLoginException.class)
                .hasFieldOrPropertyWithValue("type", TokenBlacklistService.BLACKLISTED_TOKEN_TYPE);
    }
}
