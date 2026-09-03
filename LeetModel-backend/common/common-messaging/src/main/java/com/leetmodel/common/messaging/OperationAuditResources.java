package com.leetmodel.common.messaging;

import java.util.List;

/** 操作审计在 RocketMQ 上的独立、稳定资源契约。 */
public final class OperationAuditResources {

    public static final String TOPIC = "leetmodel-operation-audit-v1";
    public static final String TAG = OperationAuditMessageCodec.EVENT_TYPE;
    public static final String CONSUMER_GROUP = "cg-audit-archive-v1";
    public static final int MAX_RECONSUME_TIMES = 5;
    public static final List<Long> RETRY_DELAYS_MILLIS = List.of(
            1_000L, 5_000L, 30_000L, 120_000L, 600_000L
    );

    private OperationAuditResources() {
    }
}
