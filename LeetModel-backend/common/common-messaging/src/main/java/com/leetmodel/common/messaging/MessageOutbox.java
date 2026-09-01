package com.leetmodel.common.messaging;

/**
 * 在调用方当前业务事务中写入可靠消息。
 */
public interface MessageOutbox {

    /**
     * 校验消息并写入本地 Outbox。
     *
     * @param logicalTopic 逻辑 Topic
     * @param tag 消息 Tag
     * @param envelope 消息信封
     * @return 事件标识
     */
    String enqueue(String logicalTopic, String tag, MessageEnvelopeV1<?> envelope);
}
