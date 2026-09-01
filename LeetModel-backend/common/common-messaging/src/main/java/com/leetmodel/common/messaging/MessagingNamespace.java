package com.leetmodel.common.messaging;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 将逻辑 Topic 和消费组映射到环境隔离的物理资源名。
 */
public final class MessagingNamespace {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,80}");

    private final String environment;

    /**
     * 创建命名空间。
     *
     * @param environment 环境标识，例如 lm-dev
     */
    public MessagingNamespace(String environment) {
        this.environment = validName(environment, "environment");
    }

    /**
     * 返回物理 Topic 名。
     *
     * @param logicalTopic 逻辑 Topic
     * @return 带环境前缀的 Topic
     */
    public String topic(String logicalTopic) {
        return environment + "%" + validName(logicalTopic, "logicalTopic");
    }

    /**
     * 返回物理消费组名。
     *
     * @param logicalGroup 逻辑消费组
     * @return 带环境前缀的消费组
     */
    public String consumerGroup(String logicalGroup) {
        return environment + "%" + validName(logicalGroup, "logicalGroup");
    }

    /**
     * 返回当前环境标识。
     *
     * @return 环境标识
     */
    public String environment() {
        return environment;
    }

    private static String validName(String value, String field) {
        String candidate = Objects.requireNonNull(value, field).trim();
        if (!NAME_PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException(field + " contains unsupported characters");
        }
        return candidate;
    }
}
