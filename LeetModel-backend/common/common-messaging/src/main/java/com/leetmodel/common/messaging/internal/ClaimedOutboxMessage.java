package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.messaging.PendingMessage;

/**
 * Relay 领取结果及其租约来源。
 *
 * @param message 已领取消息
 * @param takeover 是否从过期 SENDING 租约接管
 */
record ClaimedOutboxMessage(PendingMessage message, boolean takeover) {
}
