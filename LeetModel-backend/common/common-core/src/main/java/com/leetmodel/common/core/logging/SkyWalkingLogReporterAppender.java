package com.leetmodel.common.core.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * 通过 SkyWalking 原生 {@code /v3/logs} HTTP 协议异步上报安全 JSON 日志。
 *
 * <p>业务线程只执行有界队列 offer；网络、超时和有限重试只发生在单个 daemon
 * Reporter 线程。队列满时优先丢弃 DEBUG/INFO，所有丢弃和传输结果由 Micrometer
 * 暴露。本地 CONSOLE/LOCAL_ROLLING appender 与此链路完全独立。</p>
 */
public final class SkyWalkingLogReporterAppender extends AppenderBase<ILoggingEvent> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private boolean enabled;
    private String endpoint = "http://127.0.0.1:12800/v3/logs";
    private String service = "unknown-service";
    private String instance = "unknown-instance";
    private int queueCapacity = 2048;
    private int batchSize = 64;
    private long flushIntervalMillis = 500L;
    private long connectTimeoutMillis = 500L;
    private long requestTimeoutMillis = 1500L;
    private int maxAttempts = 2;
    private long retryBackoffMillis = 100L;
    private LeetModelJsonLayout layout;

    private volatile boolean running;
    private LinkedBlockingDeque<Envelope> queue;
    private HttpClient client;
    private URI endpointUri;
    private Thread worker;

    /**
     * 启动日志上报器，初始化内部队列、HttpClient 及守护上传线程。
     */
    @Override
    public void start() {
        if (!enabled) {
            super.start();
            return;
        }
        if (layout == null) {
            addError("SkyWalking log reporter requires LeetModelJsonLayout");
            return;
        }
        if (!layout.isStarted()) layout.start();
        try {
            endpointUri = URI.create(endpoint);
            if (!("http".equalsIgnoreCase(endpointUri.getScheme())
                    || "https".equalsIgnoreCase(endpointUri.getScheme()))) {
                throw new IllegalArgumentException("endpoint must use HTTP(S)");
            }
            if (queueCapacity < 1 || batchSize < 1 || batchSize > queueCapacity
                    || maxAttempts < 1) {
                throw new IllegalArgumentException("invalid bounded reporter configuration");
            }
        } catch (RuntimeException exception) {
            addError("Invalid SkyWalking log reporter configuration", exception);
            return;
        }
        queue = new LinkedBlockingDeque<>(queueCapacity);
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(50L, connectTimeoutMillis)))
                .build();
        running = true;
        SkyWalkingLogReporterMetrics.reporterStarted(queueCapacity);
        worker = new Thread(this::runWorker, "leetmodel-skywalking-log-reporter");
        worker.setDaemon(true);
        worker.start();
        super.start();
    }

    /**
     * 将单条日志事件投递到内存有界缓冲队列中。
     *
     * @param event Logback 日志事件对象
     */
    @Override
    protected void append(ILoggingEvent event) {
        if (!enabled || !running || event == null) return;
        event.prepareForDeferredProcessing();
        String body = layout.doLayout(event).stripTrailing();
        Envelope envelope = envelope(event, body);
        if (queue.offerLast(envelope)) {
            SkyWalkingLogReporterMetrics.accepted();
            updateDepth();
            return;
        }
        if (isLowPriority(event.getLevel())) {
            SkyWalkingLogReporterMetrics.droppedQueueLow();
            return;
        }
        Envelope lowPriority = queue.stream()
                .filter(item -> item.lowPriority)
                .findFirst()
                .orElse(null);
        if (lowPriority != null && queue.removeFirstOccurrence(lowPriority)) {
            SkyWalkingLogReporterMetrics.droppedQueueLow();
            if (queue.offerLast(envelope)) {
                SkyWalkingLogReporterMetrics.accepted();
                updateDepth();
                return;
            }
        }
        SkyWalkingLogReporterMetrics.droppedQueueHigh();
        updateDepth();
    }

    /**
     * 停止日志上报器并等待工作线程退出。
     */
    @Override
    public void stop() {
        running = false;
        Thread currentWorker = worker;
        if (currentWorker != null) {
            currentWorker.interrupt();
            try {
                currentWorker.join(Math.min(3000L,
                        Math.max(250L, requestTimeoutMillis + retryBackoffMillis)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (queue != null) {
            int abandoned = queue.size();
            queue.clear();
            if (abandoned > 0) SkyWalkingLogReporterMetrics.droppedShutdown(abandoned);
        }
        updateDepth();
        super.stop();
    }

    /**
     * 将原始日志事件包装为上报信封，解析路由与事件码。
     *
     * @param event Logback 原始日志事件
     * @param body  格式化后的 JSON 字符串
     * @return 封装了级别、事件码及时间戳的 Envelope 内部对象
     */
    private Envelope envelope(ILoggingEvent event, String body) {
        String route = null;
        String eventCode = LogEventCodes.UNCLASSIFIED;
        try {
            JsonNode parsed = JSON.readTree(body);
            route = text(parsed, LogFieldNames.ROUTE_TEMPLATE);
            String parsedEventCode = text(parsed, LogFieldNames.EVENT_CODE);
            if (parsedEventCode != null) eventCode = parsedEventCode;
        } catch (Exception ignored) {
            // LeetModelJsonLayout 已保证 JSON；若发生编码兜底，仍交由 OAP LAL 最终拒绝。
        }
        String level = event.getLevel() == null ? "UNKNOWN" : event.getLevel().levelStr;
        return new Envelope(event.getTimeStamp(), body, level, eventCode, route,
                isLowPriority(event.getLevel()));
    }

    /**
     * 执行后台批量日志上报工作循环。
     */
    private void runWorker() {
        List<Envelope> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                Envelope first = queue.pollFirst(Math.max(10L, flushIntervalMillis),
                        TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                updateDepth();
                report(batch);
            } catch (InterruptedException exception) {
                if (!running) break;
            } catch (RuntimeException exception) {
                if (!batch.isEmpty()) {
                    SkyWalkingLogReporterMetrics.failed(batch.size());
                    SkyWalkingLogReporterMetrics.droppedSend(batch.size());
                }
            } finally {
                batch.clear();
            }
        }
    }

    /**
     * 构建上报 HTTP 请求负载并执行单次批量网络发送。
     *
     * @param batch 待上报的日志信封批次
     */
    private void report(List<Envelope> batch) {
        String payload;
        try {
            payload = payload(batch);
        } catch (Exception exception) {
            SkyWalkingLogReporterMetrics.failed(batch.size());
            SkyWalkingLogReporterMetrics.droppedSend(batch.size());
            return;
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(endpointUri)
                        .timeout(Duration.ofMillis(Math.max(100L, requestTimeoutMillis)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<Void> response = client.send(request,
                        HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    SkyWalkingLogReporterMetrics.succeeded(batch.size());
                    return;
                }
                SkyWalkingLogReporterMetrics.failed(batch.size());
                if (response.statusCode() >= 400 && response.statusCode() < 500) break;
            } catch (Exception exception) {
                SkyWalkingLogReporterMetrics.failed(batch.size());
            }
            if (attempt < maxAttempts && retryBackoffMillis > 0) {
                try {
                    Thread.sleep(Math.min(1000L, retryBackoffMillis));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        SkyWalkingLogReporterMetrics.droppedSend(batch.size());
    }

    /**
     * 将待上报的信封批次序列化为符合 SkyWalking /v3/logs 格式的 JSON 数组。
     *
     * @param batch 待上报的信封列表
     * @return 符合 SkyWalking OAP 原生协议的 JSON 字符串
     * @throws Exception 当 JSON 序列化失败时抛出
     */
    private String payload(List<Envelope> batch) throws Exception {
        ArrayNode array = JSON.createArrayNode();
        for (Envelope envelope : batch) {
            ObjectNode record = array.addObject();
            record.put("timestamp", envelope.timestamp);
            record.put("service", service);
            record.put("serviceInstance", instance);
            record.put("layer", "GENERAL");
            if (envelope.route != null) record.put("endpoint", envelope.route);
            ArrayNode tags = record.putObject("tags").putArray("data");
            tag(tags, "level", envelope.level);
            tag(tags, "schema_version", LeetModelJsonLayout.SCHEMA_VERSION);
            tag(tags, "event_code", envelope.eventCode);
            record.putObject("body").putObject("json").put("json", envelope.body);
        }
        return JSON.writeValueAsString(array);
    }

    /**
     * 向标签数组添加单条键值对标签。
     *
     * @param tags  SkyWalking 标签数组节点
     * @param key   标签名
     * @param value 标签值
     */
    private void tag(ArrayNode tags, String key, String value) {
        ObjectNode tag = tags.addObject();
        tag.put("key", key);
        tag.put("value", value);
    }

    /**
     * 从 JSON 节点中提取安全非空文本字段。
     *
     * @param node  Jackson JSON 树节点
     * @param field 属性字段名
     * @return 文本属性值；不存在或非文本时返回 null
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.textValue();
    }

    /**
     * 判断日志级别是否属于低优先级（DEBUG 或 INFO）。
     *
     * @param level 日志级别
     * @return true 表示为低优先级日志，队列满时优先丢弃
     */
    private boolean isLowPriority(Level level) {
        return level == null || level.toInt() <= Level.INFO_INT;
    }

    /**
     * 刷新当前内存队列深度指标。
     */
    private void updateDepth() {
        if (queue != null) SkyWalkingLogReporterMetrics.queueDepth(queue.size());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setService(String service) {
        this.service = service;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setFlushIntervalMillis(long flushIntervalMillis) {
        this.flushIntervalMillis = flushIntervalMillis;
    }

    public void setConnectTimeoutMillis(long connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public void setRequestTimeoutMillis(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }

    public void setLayout(LeetModelJsonLayout layout) {
        this.layout = layout;
    }

    int queueDepth() {
        return queue == null ? 0 : queue.size();
    }

    private record Envelope(long timestamp, String body, String level, String eventCode,
                            String route, boolean lowPriority) {
    }
}
