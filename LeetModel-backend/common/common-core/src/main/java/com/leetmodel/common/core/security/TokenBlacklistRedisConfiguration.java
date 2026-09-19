package com.leetmodel.common.core.security;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 黑名单 Redis 客户端超时配置。
 *
 * <p>安全状态 Redis 只承载 Token 黑名单，允许在故障时降级，因此命令与连接超时必须远小于
 * Lettuce 默认值：否则 Redis 不可用时请求会排队等待默认 60 秒命令超时，把 fail-open
 * 降级变成事实上的全站阻塞。</p>
 *
 * <p>Redis 客户端类型属于可选依赖，因此实际生效的配置放在嵌套类中并用类名条件保护：
 * 外层类不引用任何 Redis 类型，未引入 Redis 的服务可以安全加载并直接跳过。</p>
 */
@AutoConfiguration
public class TokenBlacklistRedisConfiguration {

    /**
     * 仅在存在 Lettuce 连接工厂时生效的超时配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory")
    static class LettuceTimeoutConfiguration {

        /**
         * 为 Redis 客户端设置短超时。
         *
         * @param properties 黑名单配置
         * @return Lettuce 客户端构建定制器
         */
        @Bean
        LettuceClientConfigurationBuilderCustomizer tokenBlacklistRedisTimeoutCustomizer(
                TokenBlacklistProperties properties) {
            Duration timeout = Duration.ofMillis(properties.getRedisTimeoutMs());
            return builder -> builder
                    .commandTimeout(timeout)
                    .clientOptions(ClientOptions.builder()
                            .socketOptions(SocketOptions.builder().connectTimeout(timeout).build())
                            .build());
        }
    }
}
