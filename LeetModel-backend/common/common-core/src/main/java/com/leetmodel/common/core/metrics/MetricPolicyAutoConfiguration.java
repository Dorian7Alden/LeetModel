package com.leetmodel.common.core.metrics;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 全服务共享的指标标签安全边界。
 */
@AutoConfiguration
@ConditionalOnClass(MeterFilter.class)
public class MetricPolicyAutoConfiguration {

    /**
     * 注册拒绝高基数业务标签的全局指标过滤器。
     *
     * @return 拦截包含高基数标签的 MeterFilter 实例
     */
    @Bean
    public MeterFilter leetModelForbiddenMetricTagFilter() {
        return MeterFilter.deny(id -> id.getTags().stream()
                .anyMatch(tag -> MetricTagPolicy.isForbiddenIdTag(tag.getKey())));
    }
}
