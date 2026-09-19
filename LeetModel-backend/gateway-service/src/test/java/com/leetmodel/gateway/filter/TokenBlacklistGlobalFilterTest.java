package com.leetmodel.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.security.TokenBlacklistPolicy;
import com.leetmodel.common.core.security.TokenBlacklistProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenBlacklistGlobalFilterTest {

    private static final String TOKEN = "header.payload.signature";

    private ReactiveStringRedisTemplate redisTemplate;
    private TokenBlacklistProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private TokenBlacklistGlobalFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        properties = new TokenBlacklistProperties();
        meterRegistry = new SimpleMeterRegistry();
        filter = new TokenBlacklistGlobalFilter(redisTemplate, properties, new ObjectMapper(), meterRegistry);
    }

    @Test
    @DisplayName("命中黑名单时返回 401 且不再转发到下游")
    void revokedTokenIsRejected() {
        when(redisTemplate.hasKey(expectedKey())).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
        assertThat(meterRegistry.counter("auth_token_blacklist_hit_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("未命中黑名单时继续转发")
    void activeTokenIsForwarded() {
        when(redisTemplate.hasKey(expectedKey())).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("无 Token 的请求不查询 Redis")
    void requestWithoutTokenSkipsRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/public/problems").build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("Redis 异常时默认放行并累计降级计数")
    void redisFailureFailsOpenByDefault() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(meterRegistry.counter("auth_token_blacklist_degraded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("fail-closed 配置下 Redis 异常返回 401")
    void redisFailureFailsClosedWhenConfigured() {
        properties.setFailClosed(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Redis 长时间无响应时按超时降级，不阻塞请求")
    void redisTimeoutDegradesWithoutBlocking() {
        properties.setRedisTimeoutMs(50L);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.never());
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        assertThat(meterRegistry.counter("auth_token_blacklist_degraded_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("关闭开关后不查询黑名单")
    void disabledSwitchSkipsRedis() {
        properties.setEnabled(false);
        MockServerWebExchange exchange = exchangeWithToken();
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    private MockServerWebExchange exchangeWithToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/problems")
                .header("satoken", TOKEN)
                .build();
        return MockServerWebExchange.from(request);
    }

    private String expectedKey() {
        return TokenBlacklistPolicy.key(properties.getKeyPrefix(), TOKEN);
    }
}
