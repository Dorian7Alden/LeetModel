package com.leetmodel.common.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LeetModel 业务缓存配置。
 */
@ConfigurationProperties("leetmodel.cache")
public class CacheProperties {

    private boolean enabled;
    private String environment = "dev";
    private long maximumWeight = 64L * 1024L * 1024L;
    private int maximumValueBytes = 512 * 1024;
    private Duration degradedTtl = Duration.ofSeconds(5);
    private Duration reconcileInterval = Duration.ofSeconds(5);
    private double ttlJitter = 0.2D;
    private final Redis redis = new Redis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public long getMaximumWeight() {
        return maximumWeight;
    }

    public void setMaximumWeight(long maximumWeight) {
        this.maximumWeight = maximumWeight;
    }

    public int getMaximumValueBytes() {
        return maximumValueBytes;
    }

    public void setMaximumValueBytes(int maximumValueBytes) {
        this.maximumValueBytes = maximumValueBytes;
    }

    public Duration getDegradedTtl() {
        return degradedTtl;
    }

    public void setDegradedTtl(Duration degradedTtl) {
        this.degradedTtl = degradedTtl;
    }

    public Duration getReconcileInterval() {
        return reconcileInterval;
    }

    public void setReconcileInterval(Duration reconcileInterval) {
        this.reconcileInterval = reconcileInterval;
    }

    public double getTtlJitter() {
        return ttlJitter;
    }

    public void setTtlJitter(double ttlJitter) {
        this.ttlJitter = ttlJitter;
    }

    public Redis getRedis() {
        return redis;
    }

    /**
     * 独立业务 Redis 连接配置。
     */
    public static class Redis {
        private String host = "localhost";
        private int port = 6380;
        private int database;
        private String password;
        private boolean ssl;
        private Duration connectTimeout = Duration.ofMillis(200);
        private Duration commandTimeout = Duration.ofMillis(100);

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getCommandTimeout() {
            return commandTimeout;
        }

        public void setCommandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout;
        }
    }
}
