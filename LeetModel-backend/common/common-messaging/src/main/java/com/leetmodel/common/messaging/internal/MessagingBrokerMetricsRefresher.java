package com.leetmodel.common.messaging.internal;

import org.springframework.scheduling.annotation.Scheduled;

/** 周期刷新需要主动访问 Broker 的消费位点和 DLQ 指标。 */
public final class MessagingBrokerMetricsRefresher {

    private final RocketMqConsumerControl consumers;
    private final RocketMqDeadLetterOperations deadLetters;
    private final MessagingMetrics metrics;

    public MessagingBrokerMetricsRefresher(
            RocketMqConsumerControl consumers,
            RocketMqDeadLetterOperations deadLetters,
            MessagingMetrics metrics
    ) {
        this.consumers = consumers;
        this.deadLetters = deadLetters;
        this.metrics = metrics;
    }

    /** Broker 读取失败由 availability 指标表达，不中断业务调度线程。 */
    @Scheduled(fixedDelayString = "${leetmodel.messaging.metrics.broker-refresh-ms:15000}")
    public void refresh() {
        metrics.updateBroker(consumers.backlogs(), deadLetters.summaries());
    }
}
