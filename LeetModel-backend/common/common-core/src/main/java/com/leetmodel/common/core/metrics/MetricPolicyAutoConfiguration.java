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
     * 拒绝任何携带高基数业务标识标签的指标。
     *
     * @return 全局 MeterFilter
     */
    @Bean
    public MeterFilter leetModelForbiddenMetricTagFilter() {
        return MeterFilter.deny(id -> id.getTags().stream()
                .anyMatch(tag -> MetricTagPolicy.isForbiddenIdTag(tag.getKey())));
    }
}
