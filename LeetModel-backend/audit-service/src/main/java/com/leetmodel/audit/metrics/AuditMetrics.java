package com.leetmodel.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** 审计管道低基数指标；绝不把 operation/event/trace/user 标识作为标签。 */
@Component
public class AuditMetrics {
    private final Counter consumed;
    private final Counter duplicate;
    private final Counter rejected;
    private final Counter failed;
    private final Counter dlq;
    private final Counter incomplete;
    private final Counter monitorFailures;
    private final Timer processing;
    private final AtomicLong inboxProcessing = new AtomicLong();
    private final AtomicLong incompleteOperations = new AtomicLong();

    private final MeterRegistry registry;

    public AuditMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.consumed = counter("consumed");
        this.duplicate = counter("duplicate");
        this.rejected = counter("rejected");
        this.failed = counter("failed");
        this.dlq = Counter.builder("audit.consumer.dlq")
                .description("审计消息进入 DLQ 的次数").register(registry);
        this.incomplete = Counter.builder("audit.integrity.incomplete")
                .description("超过 deadline 仍缺少终态的审计操作").register(registry);
        this.monitorFailures = Counter.builder("audit.integrity.monitor.failures")
                .description("审计完整性扫描失败次数").register(registry);
        this.processing = Timer.builder("audit.consumer.processing")
                .description("审计消息消费处理耗时").publishPercentileHistogram().register(registry);
        registry.gauge("audit.inbox.processing", inboxProcessing);
        registry.gauge("audit.integrity.incomplete.active", incompleteOperations);
    }

    private Counter counter(String result) {
        return Counter.builder("audit.archive.events")
                .description("审计消息归档结果")
                .tag("result", result).register(registry);
    }

    public void consumed() { consumed.increment(); }
    public void duplicate() { duplicate.increment(); }
    public void rejected() { rejected.increment(); }
    public void failed() { failed.increment(); }
    public void dlq() { dlq.increment(); }
    public void incomplete(long count) {
        long previous = incompleteOperations.getAndSet(Math.max(0, count));
        if (count > previous) incomplete.increment(count - previous);
    }
    public void processing(long nanos) { processing.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS); }
    public void inboxProcessing(long count) { inboxProcessing.set(Math.max(0, count)); }
    public void monitorFailure() { monitorFailures.increment(); }
}
