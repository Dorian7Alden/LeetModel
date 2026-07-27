package com.leetmodel.gateway.filter;

import com.leetmodel.common.core.util.TraceIdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway TraceId 全局过滤器 —— 全链路追踪的入口。
 *
 * <p>职责：
 * <ul>
 *   <li>检查请求头 {@code X-Trace-Id}：有则复用，无则生成 UUID（32 位去横线）</li>
 *   <li>将 TraceId 写入 MDC（依赖 Reactor 自动上下文传播）</li>
 *   <li>将 TraceId 注入转发请求头（下游服务通过 Header 提取）</li>
 *   <li>请求结束后清理 MDC（防止线程池复用时串扰）</li>
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
 *
 * @author LeetModel
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    static final String TRACE_ID_HEADER = TraceIdUtil.TRACE_ID_HEADER;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ① 从请求头提取或生成
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            log.debug("生成新 TraceId: {}", traceId);
        } else {
            log.debug("复用上游 TraceId: {}", traceId);
        }

        // ② 写入 MDC（Hooks.enableAutomaticContextPropagation 保证传播到后续线程）
        TraceIdUtil.setTraceId(traceId);

        // ③ 注入到转发请求头（下游服务从 Header 提取）
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // ④ doFinally 覆盖所有终止信号（complete/error/cancel），确保 MDC 一定被清理
        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    log.debug("请求结束，清理 MDC (signal: {})", signalType);
                    TraceIdUtil.removeTraceId();
                });
    }
}
