package com.leetmodel.common.messaging;

/**
 * Inbox 幂等执行结果。
 */
public enum InboxResult {
    /** 首次消费并完成本地事务。 */
    CONSUMED,
    /** 同一消费组已经成功处理过该事件。 */
    DUPLICATE
}
