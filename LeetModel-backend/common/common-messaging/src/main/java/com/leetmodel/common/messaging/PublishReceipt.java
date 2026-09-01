package com.leetmodel.common.messaging;

/**
 * Broker 接收消息后的回执。
 *
 * @param brokerMessageId Broker 消息标识
 */
public record PublishReceipt(String brokerMessageId) {
}
