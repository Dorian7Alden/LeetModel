package com.leetmodel.common.core.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 重复故障日志的有界窗口配置。 */
@ConfigurationProperties(prefix = "leetmodel.logging.rate-limit")
public class LogRateLimitProperties {

    private boolean enabled = true;
    private Duration summaryInterval = Duration.ofMinutes(1);
    private int maxKeys = 256;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getSummaryInterval() {
        return summaryInterval;
    }

    public void setSummaryInterval(Duration summaryInterval) {
        this.summaryInterval = summaryInterval;
    }

    public int getMaxKeys() {
        return maxKeys;
    }

    public void setMaxKeys(int maxKeys) {
        this.maxKeys = maxKeys;
    }
}
