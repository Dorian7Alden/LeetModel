package com.leetmodel.common.core.management;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 为每个可启动服务提供稳定存在的 Degraded 健康分组基线。
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
public class ObservabilityHealthAutoConfiguration {

    /**
     * 注册服务默认降级状态健康指示器。
     *
     * <p>确保所有微服务在未启用缓存或消息时，Degraded 探针分组也具备默认存活指标。</p>
     *
     * @return 默认返回 UP 状态的 HealthIndicator 实例
     */
    @Bean(name = "degradedStateHealthIndicator")
    @ConditionalOnMissingBean(name = "degradedStateHealthIndicator")
    public HealthIndicator degradedStateHealthIndicator() {
        return () -> Health.up().withDetail("mode", "normal").build();
    }
}
