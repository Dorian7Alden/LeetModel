package com.leetmodel.common.messaging;

/**
 * 将 Inbox 去重与领域状态推进包裹在同一本地事务中。
 */
public interface MessageInbox {

    /**
     * 首次消息执行领域动作，重复消息直接返回。
     *
     * @param logicalConsumerGroup 逻辑消费组
     * @param envelope 已完成契约校验的消息
     * @param domainAction 短事务领域动作
     * @return 消费结果
     */
    InboxResult executeOnce(
            String logicalConsumerGroup,
            MessageEnvelopeV1<?> envelope,
            Runnable domainAction
    );
}
