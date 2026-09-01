package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 全服务共享的日志故障限频、抑制指标与有界 Reporter 指标配置。 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration"
})
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

    @Bean
    @ConditionalOnMissingBean(name = "skyWalkingLogReporterMeterBinder")
    public MeterBinder skyWalkingLogReporterMeterBinder() {
        return SkyWalkingLogReporterMetrics.meterBinder();
    }
}
