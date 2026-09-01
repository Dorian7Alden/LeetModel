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
     * 即使服务没有缓存或消息依赖，Degraded 分组也必须是可查询契约。
     *
     * @return 默认未降级状态
     */
    @Bean(name = "degradedStateHealthIndicator")
    @ConditionalOnMissingBean(name = "degradedStateHealthIndicator")
    public HealthIndicator degradedStateHealthIndicator() {
        return () -> Health.up().withDetail("mode", "normal").build();
    }
}
