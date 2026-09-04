package com.leetmodel.common.messaging;

/**
 * 本地事务可靠消息 Outbox 写入端口。
 *
 * <p>供各业务微服务在本地事务内调用，原子暂存待发布事件信封，由独立 Relay 异步投递。</p>
 */
public interface MessageOutbox {

    /**
     * 校验消息契约并写入当前服务本地 Outbox 表。
     *
     * @param logicalTopic 逻辑 Topic 名称，不能为 null 或空
     * @param tag          消息过滤 Tag，不能为 null 或空
     * @param envelope     符合 V1 契约的标准化消息信封，不能为 null
     * @return 写入成功的全局唯一事件标识（eventId）
     */
    String enqueue(
            String logicalTopic,
            String tag,
            MessageEnvelopeV1<?> envelope
    );
}
