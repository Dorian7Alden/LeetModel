package com.leetmodel.common.messaging.internal;

/**
 * 消息 Outbox 状态。
 */
public enum OutboxStatus {
    /** 等待投递或等待重试。 */
    PENDING,
    /** 已被 Relay 短租约领取。 */
    SENDING,
    /** Broker 已确认接收。 */
    PUBLISHED,
    /** 稳定配置或契约错误，需要人工修复。 */
    BLOCKED
}
