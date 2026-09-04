package com.leetmodel.common.core.logging;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 进程内 SkyWalking 日志 Reporter 的低基数 Prometheus 状态。 */
public final class SkyWalkingLogReporterMetrics {

    public static final String EVENTS_METRIC = "leetmodel.logging.reporter.events";
    public static final String QUEUE_DEPTH_METRIC = "leetmodel.logging.reporter.queue.depth";
    public static final String QUEUE_CAPACITY_METRIC = "leetmodel.logging.reporter.queue.capacity";
    public static final String CONNECTED_METRIC = "leetmodel.logging.reporter.connected";

    private static final AtomicLong ACCEPTED = new AtomicLong();
    private static final AtomicLong SUCCEEDED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static final AtomicLong DROPPED_QUEUE_LOW = new AtomicLong();
    private static final AtomicLong DROPPED_QUEUE_HIGH = new AtomicLong();
    private static final AtomicLong DROPPED_SEND = new AtomicLong();
    private static final AtomicLong DROPPED_SHUTDOWN = new AtomicLong();
    private static final AtomicLong RECOVERED = new AtomicLong();
    private static final AtomicInteger QUEUE_DEPTH = new AtomicInteger();
    private static final AtomicInteger QUEUE_CAPACITY = new AtomicInteger();
    private static final AtomicInteger CONNECTED = new AtomicInteger();
    private static final AtomicInteger FAILED_SINCE_LAST_SUCCESS = new AtomicInteger();

    private SkyWalkingLogReporterMetrics() {
    }

    /**
     * 构造 MeterBinder 以便向 Spring Actuator 注册日志上报指标。
     *
     * @return 用于绑定指标的 MeterBinder 实例
     */
    public static MeterBinder meterBinder() {
        return SkyWalkingLogReporterMetrics::bind;
    }

    /**
     * 记录 Reporter 启动时的队列容量配置。
     *
     * @param capacity 队列最大容量
     */
    static void reporterStarted(int capacity) {
        QUEUE_CAPACITY.set(Math.max(0, capacity));
        QUEUE_DEPTH.set(0);
        CONNECTED.set(0);
    }

    static void queueDepth(int depth) {
        QUEUE_DEPTH.set(Math.max(0, depth));
    }

    static void accepted() {
        ACCEPTED.incrementAndGet();
    }

    static void succeeded(int count) {
        SUCCEEDED.addAndGet(count);
        CONNECTED.set(1);
        if (FAILED_SINCE_LAST_SUCCESS.getAndSet(0) == 1) RECOVERED.incrementAndGet();
    }

    static void failed(int count) {
        FAILED.addAndGet(count);
        CONNECTED.set(0);
        FAILED_SINCE_LAST_SUCCESS.set(1);
    }

    static void droppedQueueLow() {
        DROPPED_QUEUE_LOW.incrementAndGet();
    }

    static void droppedQueueHigh() {
        DROPPED_QUEUE_HIGH.incrementAndGet();
    }

    static void droppedSend(int count) {
        DROPPED_SEND.addAndGet(count);
    }

    static void droppedShutdown(int count) {
        DROPPED_SHUTDOWN.addAndGet(count);
    }

    private static void bind(MeterRegistry registry) {
        counter(registry, ACCEPTED, "accepted", "none");
        counter(registry, SUCCEEDED, "succeeded", "none");
        counter(registry, FAILED, "failed", "transport");
        counter(registry, DROPPED_QUEUE_LOW, "dropped", "queue_full_low_priority");
        counter(registry, DROPPED_QUEUE_HIGH, "dropped", "queue_full_high_priority");
        counter(registry, DROPPED_SEND, "dropped", "send_exhausted");
        counter(registry, DROPPED_SHUTDOWN, "dropped", "shutdown");
        counter(registry, RECOVERED, "recovered", "transport");
        Gauge.builder(QUEUE_DEPTH_METRIC, QUEUE_DEPTH, AtomicInteger::get)
                .description("Current bounded SkyWalking log reporter queue depth")
                .register(registry);
        Gauge.builder(QUEUE_CAPACITY_METRIC, QUEUE_CAPACITY, AtomicInteger::get)
                .description("Configured SkyWalking log reporter queue capacity")
                .register(registry);
        Gauge.builder(CONNECTED_METRIC, CONNECTED, AtomicInteger::get)
                .description("Whether the last SkyWalking log report attempt succeeded")
                .register(registry);
    }

    private static void counter(MeterRegistry registry, AtomicLong value,
                                String outcome, String cause) {
        FunctionCounter.builder(EVENTS_METRIC, value, AtomicLong::doubleValue)
                .description("SkyWalking log reporter event outcomes")
                .tag("outcome", outcome)
                .tag("cause", cause)
                .register(registry);
    }

    static Snapshot snapshot() {
        return new Snapshot(ACCEPTED.get(), SUCCEEDED.get(), FAILED.get(),
                DROPPED_QUEUE_LOW.get(), DROPPED_QUEUE_HIGH.get(), DROPPED_SEND.get(),
                DROPPED_SHUTDOWN.get(),
                RECOVERED.get(), QUEUE_DEPTH.get(), QUEUE_CAPACITY.get(), CONNECTED.get());
    }

    static void resetForTest() {
        ACCEPTED.set(0);
        SUCCEEDED.set(0);
        FAILED.set(0);
        DROPPED_QUEUE_LOW.set(0);
        DROPPED_QUEUE_HIGH.set(0);
        DROPPED_SEND.set(0);
        DROPPED_SHUTDOWN.set(0);
        RECOVERED.set(0);
        QUEUE_DEPTH.set(0);
        QUEUE_CAPACITY.set(0);
        CONNECTED.set(0);
        FAILED_SINCE_LAST_SUCCESS.set(0);
    }

    record Snapshot(long accepted, long succeeded, long failed,
                    long droppedQueueLow, long droppedQueueHigh, long droppedSend,
                    long droppedShutdown, long recovered, int queueDepth,
                    int queueCapacity, int connected) {
    }
}
