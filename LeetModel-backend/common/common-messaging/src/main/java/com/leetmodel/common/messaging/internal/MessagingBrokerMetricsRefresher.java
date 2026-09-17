package com.leetmodel.common.messaging.internal;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * 周期刷新 Broker 真实消费位点和 DLQ 指标的后台定时任务。
 *
 * <p>主动轮询 RocketMQ Broker 采集消费者积压量与死信队列消息计数，更新 Prometheus 仪表。</p>
 */
public final class MessagingBrokerMetricsRefresher {

    private final RocketMqConsumerControl consumers;
    private final RocketMqDeadLetterOperations deadLetters;
    private final MessagingMetrics metrics;

    /**
     * 构造 Broker 指标定时刷新器。
     *
     * @param consumers   消费者控制器
     * @param deadLetters 死信队列操作工具
     * @param metrics     消息指标门面
     */
    public MessagingBrokerMetricsRefresher(
            RocketMqConsumerControl consumers,
            RocketMqDeadLetterOperations deadLetters,
            MessagingMetrics metrics
    ) {
        this.consumers = consumers;
        this.deadLetters = deadLetters;
        this.metrics = metrics;
    }

    /**
     * 定时执行指标刷新（读取失败时仅由 availability 指标表达，不中断定时任务调度）。
     */
    @Scheduled(fixedDelayString = "${leetmodel.messaging.metrics.broker-refresh-ms:15000}")
    public void refresh() {
        metrics.updateBroker(consumers.backlogs(), deadLetters.summaries());
    }
}
