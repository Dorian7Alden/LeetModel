package com.leetmodel.gateway.filter;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.security.TokenBlacklistPolicy;
import com.leetmodel.common.core.security.TokenBlacklistProperties;
import com.leetmodel.common.core.security.TokenFingerprint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 网关 Token 黑名单校验过滤器。
 *
 * <p>Sa-Token 过滤器只校验 JWT 签名与有效期，无状态模式下无法感知服务端登出；
 * 本过滤器在签名校验通过后按 Token 指纹查询安全状态 Redis，命中即返回 401。
 * 查询走响应式客户端，不使用阻塞调用占用事件循环线程。</p>
 */
@Slf4j
@Component
public class TokenBlacklistGlobalFilter implements GlobalFilter, Ordered {

    /** 在 Sa-Token 过滤器完成验签之后执行。 */
    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** Sa-Token 默认令牌名，配置缺失时使用。 */
    private static final String DEFAULT_TOKEN_NAME = "satoken";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TokenBlacklistProperties properties;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter degradedCounter;

    public TokenBlacklistGlobalFilter(ReactiveStringRedisTemplate redisTemplate,
                                      TokenBlacklistProperties properties,
                                      ObjectMapper objectMapper,
                                      MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.hitCounter = meterRegistry.counter("auth_token_blacklist_hit_total");
        this.degradedCounter = meterRegistry.counter("auth_token_blacklist_degraded_total");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = resolveToken(exchange);
        if (!properties.isEnabled() || token == null) {
            return chain.filter(exchange);
        }
        String key = TokenBlacklistPolicy.key(properties.getKeyPrefix(), token);
        return redisTemplate.hasKey(key)
                // Redis 不可用时 Lettuce 会先重连而不是立即失败，必须显式限时才能进入降级
                .timeout(Duration.ofMillis(properties.getRedisTimeoutMs()))
                .flatMap(revoked -> revoked
                        ? reject(exchange, token)
                        : chain.filter(exchange))
                .onErrorResume(exception -> degrade(exchange, chain, exception));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    /**
     * 从请求头读取 Token。
     *
     * @param exchange 当前请求上下文
     * @return Token 原文；缺失时返回 null
     */
    private String resolveToken(ServerWebExchange exchange) {
        SaTokenConfig config = SaManager.getConfig();
        String tokenName = config == null || config.getTokenName() == null
                ? DEFAULT_TOKEN_NAME
                : config.getTokenName();
        String token = exchange.getRequest().getHeaders().getFirst(tokenName);
        if (token == null || token.isBlank()) {
            return null;
        }
        return token;
    }

    /**
     * 拒绝命中黑名单的请求。
     *
     * @param exchange 当前请求上下文
     * @param token    Token 原文，仅用于计算不可逆指纹前缀
     * @return 响应写入结果
     */
    private Mono<Void> reject(ServerWebExchange exchange, String token) {
        hitCounter.increment();
        log.info("请求携带已登出 Token，网关拒绝: fingerprint={}",
                TokenFingerprint.logPrefix(TokenFingerprint.of(token)));
        return UnauthorizedResponses.write(exchange, objectMapper,
                UnauthorizedResponses.BLACKLISTED_MESSAGE);
    }

    /**
     * Redis 不可用时的降级处理。
     *
     * @param exchange  当前请求上下文
     * @param chain     后续过滤器链
     * @param exception Redis 访问异常
     * @return 拒绝响应或继续执行请求
     */
    private Mono<Void> degrade(ServerWebExchange exchange, GatewayFilterChain chain,
                               Throwable exception) {
        degradedCounter.increment();
        log.warn("Token 黑名单查询降级: failClosed={}, path={}, exceptionType={}",
                properties.isFailClosed(), exchange.getRequest().getPath(), exception.getClass().getSimpleName());
        if (properties.isFailClosed()) {
            return UnauthorizedResponses.write(exchange, objectMapper,
                    UnauthorizedResponses.UNAUTHENTICATED_MESSAGE);
        }
        return chain.filter(exchange);
    }
}
