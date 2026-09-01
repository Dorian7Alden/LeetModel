package com.leetmodel.common.api.dto;

/** RocketMQ 消费器运行状态，不包含连接凭据。 */
public record MessagingConsumerDTO(String consumerGroup, String topic, boolean paused, boolean running) {
}
