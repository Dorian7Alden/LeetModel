package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.PermanentPublishException;
import com.leetmodel.common.messaging.PublishReceipt;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

/**
 * 使用 RocketMQ Spring 历史 Remoting 客户端同步发布普通消息。
 */
public final class RocketMqMessagePublisher implements MessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final long timeoutMs;

    /**
     * 创建 RocketMQ 发布器。
     *
     * @param rocketMQTemplate RocketMQ 模板
     * @param timeoutMs Broker ACK 超时
     */
    public RocketMqMessagePublisher(RocketMQTemplate rocketMQTemplate, long timeoutMs) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 向 RocketMQ Broker 同步发送已领取的待发消息。
     *
     * @param pending 待发布的本地 Outbox 消息实体
     * @return 包含 Broker 消息全局 ID 的发布回执
     * @throws PermanentPublishException 若发生不可自愈的拓扑或权限配置错误（如 TOPIC 不存在、无权限）
     */
    @Override
    public PublishReceipt publish(PendingMessage pending) {
        Message<byte[]> message = MessageBuilder
                .withPayload(pending.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setHeader(RocketMQHeaders.KEYS, pending.messageKey())
                .build();
        try {
            SendResult result = rocketMQTemplate.syncSend(
                    pending.topic() + ":" + pending.tag(),
                    message,
                    timeoutMs
            );
            return new PublishReceipt(result.getMsgId());
        } catch (RuntimeException exception) {
            if (isStableConfigurationError(exception)) {
                throw new PermanentPublishException("RocketMQ resource or message configuration is invalid", exception);
            }
            throw exception;
        }
    }

    /**
     * 递归检查异常链，判断是否属于静态资源或权限配置错误（此类错误重试无法恢复，需直接阻断 Outbox）。
     *
     * @param error 捕获的异常对象
     * @return 若包含不可恢复的配置错误特征返回 true，否则返回 false
     */
    private boolean isStableConfigurationError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("No route info")
                    || message.contains("TOPIC_NOT_EXIST")
                    || message.contains("MESSAGE_ILLEGAL")
                    || message.contains("NO_PERMISSION"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
