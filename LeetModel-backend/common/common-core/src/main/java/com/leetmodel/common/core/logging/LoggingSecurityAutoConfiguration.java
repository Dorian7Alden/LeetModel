package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 全服务共享的日志故障限频与抑制指标配置。 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(LogRateLimitProperties.class)
public class LoggingSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public FailureLogLimiter leetModelFailureLogLimiter(
            MeterRegistry registry, LogRateLimitProperties properties) {
        return new FailureLogLimiter(registry, properties);
    }
}
