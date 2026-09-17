package com.leetmodel.common.messaging;

/**
 * Outbox Relay 使用的消息传输端口。
 */
public interface MessagePublisher {

    /**
     * 向 Broker 同步发送已校验的消息。
     *
     * @param message 待发送消息
     * @return Broker 回执
     */
    PublishReceipt publish(PendingMessage message);
}
