package com.leetmodel.gateway.filter;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.SkyWalkingCorrelation;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gateway TraceId 全局过滤器 —— 全链路追踪的入口。
 *
 * <p>职责：
 * <ul>
 *   <li>删除公网请求伪造的全部内部关联头</li>
 *   <li>在唯一受信入口生成 UUID traceId</li>
 *   <li>将关联快照写入 Reactor Context，线程切换时自动恢复 MDC</li>
 *   <li>将 TraceId 注入转发请求头（下游服务通过 Header 提取）</li>
 *   <li>请求终止后恢复线程原 MDC，防止复用线程串扰</li>
 * </ul>
 *
 * <p>为何不用 SaReactorFilter 嵌入？TraceId 生成是横切关注点，
 * 与鉴权逻辑独立。独立 GlobalFilter 通过 {@code @Order} 控制优先执行，
 * 职责清晰、可单独测试。</p>
 *
 * <h3>WebFlux MDC 传播</h3>
 * <p>Gateway 基于 Netty + Reactor，一个请求可能经过多个线程。
 * MDC 本质是 ThreadLocal，线程切换后会丢失。
 * 解决方案：在启动类中调用 {@code Hooks.enableAutomaticContextPropagation()}，
 * 将 MDC 上下文自动复制到 Reactor Context 并在线程切换时恢复。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ① 公网头不可成为内部关联事实。
        String traceId = CorrelationContext.newId();
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    CorrelationContext.INTERNAL_CORRELATION_HEADERS.forEach(headers::remove);
                    headers.set(TRACE_ID_HEADER, traceId);
                })
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();
        mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        // ② 组装阶段用作用域保护当前线程，运行阶段从 Reactor Context 恢复。
        CorrelationSnapshot snapshot = SkyWalkingCorrelation.enrich(
                CorrelationSnapshot.EMPTY.withTraceId(traceId));
        long started = System.nanoTime();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            SkyWalkingCorrelation.bindBusinessTraceId(traceId);
            return chain.filter(mutatedExchange)
                    .doOnError(failure::set)
                    .doFinally(ignoredSignal -> {
                        if (!isActuatorRequest(mutatedExchange)) {
                            writeAccessEvent(mutatedExchange, traceId, started, failure.get());
                        }
                    })
                    .contextWrite(context -> context.put(CorrelationContext.REACTOR_CONTEXT_KEY, snapshot));
        }
    }

    private void writeAccessEvent(ServerWebExchange exchange, String traceId,
                                  long started, Throwable failure) {
        int responseStatus = exchange.getResponse().getStatusCode() == null
                ? 200 : exchange.getResponse().getStatusCode().value();
        int status = failure == null ? responseStatus : Math.max(500, responseStatus);
        boolean failed = failure != null || status >= 500;
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeTemplate = route == null ? "UNMATCHED" : "route:" + route.getId();
        LoggingEventBuilder event = failed ? log.atWarn() : log.atInfo();
        event.addKeyValue(LogFieldNames.EVENT_CODE, failed
                        ? LogEventCodes.HTTP_REQUEST_FAILED : LogEventCodes.HTTP_REQUEST_COMPLETED)
                .addKeyValue(LogFieldNames.TRACE_ID, traceId)
                .addKeyValue(LogFieldNames.HTTP_METHOD, exchange.getRequest().getMethod().name())
                .addKeyValue(LogFieldNames.ROUTE_TEMPLATE, routeTemplate)
                .addKeyValue(LogFieldNames.STATUS_CODE, status)
                .addKeyValue(LogFieldNames.DURATION_MS,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        if (failure != null) event.setCause(failure);
        event.log(failed ? "HTTP request failed" : "HTTP request completed");
    }

    private boolean isActuatorRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        return path.equals("/actuator") || path.startsWith("/actuator/");
    }
}
