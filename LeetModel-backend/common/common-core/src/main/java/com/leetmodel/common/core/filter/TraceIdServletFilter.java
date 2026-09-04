package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import com.leetmodel.common.core.telemetry.SkyWalkingCorrelation;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Servlet 进站请求链路追踪过滤器。
 *
 * <p>从请求头提取 X-Trace-Id（合法保留，缺失或非法则生成新 UUID），绑定线程 MDC；
 * 响应头回写 X-Trace-Id，请求退出时记录 HTTP_REQUEST_COMPLETED 并彻底清理 MDC 防止线程池复用污染。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdServletFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdServletFilter.class);

    /** 跨服务透传的 HTTP 请求头名称 */
    static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

    /**
     * 拦截 HTTP 请求，绑定全链路追踪 ID 并维护请求上下文生命周期。
     *
     * @param request     当前 HTTP 请求对象
     * @param response    当前 HTTP 响应对象
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException      I/O 读写异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String inboundTraceId = request.getHeader(TRACE_ID_HEADER);
        String traceId = CorrelationContext.isValidHttpId(inboundTraceId)
                ? inboundTraceId.trim() : CorrelationContext.newId();
        String inboundOperationId = request.getHeader(CorrelationContext.OPERATION_ID_HEADER);
        String operationId = CorrelationContext.isValidHttpId(inboundOperationId)
                ? inboundOperationId.trim() : null;
        CorrelationSnapshot snapshot = SkyWalkingCorrelation.enrich(CorrelationSnapshot.EMPTY
                .withTraceId(traceId)
                .withOperationId(operationId));
        long started = System.nanoTime();
        Throwable failure = null;

        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            SkyWalkingCorrelation.bindBusinessTraceId(traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            try {
                filterChain.doFilter(request, response);
            } catch (IOException | ServletException | RuntimeException exception) {
                failure = exception;
                throw exception;
            } finally {
                if (!isActuatorRequest(request)) {
                    writeAccessEvent(request, response, traceId, started, failure);
                }
            }
        }
    }

    /**
     * 记录 HTTP 请求完成或失败的结构化访问日志。
     *
     * @param request   当前 HTTP 请求对象
     * @param response  当前 HTTP 响应对象
     * @param traceId   全局唯一追踪 ID
     * @param started   请求开始纳秒时间戳
     * @param failure   捕获的底层异常，成功时为 null
     */
    private void writeAccessEvent(HttpServletRequest request, HttpServletResponse response,
                                  String traceId, long started, Throwable failure) {
        int status = failure == null ? response.getStatus() : Math.max(500, response.getStatus());
        boolean failed = failure != null || status >= 500;
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String routeTemplate = matched == null ? "UNMATCHED" : matched.toString();
        LoggingEventBuilder event = failed ? log.atWarn() : log.atInfo();
        event.addKeyValue(LogFieldNames.EVENT_CODE, failed
                        ? LogEventCodes.HTTP_REQUEST_FAILED : LogEventCodes.HTTP_REQUEST_COMPLETED)
                .addKeyValue(LogFieldNames.TRACE_ID, traceId)
                .addKeyValue(LogFieldNames.HTTP_METHOD, request.getMethod())
                .addKeyValue(LogFieldNames.ROUTE_TEMPLATE, routeTemplate)
                .addKeyValue(LogFieldNames.STATUS_CODE, status)
                .addKeyValue(LogFieldNames.DURATION_MS,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        if (failure != null) event.setCause(failure);
        event.log(failed ? "HTTP request failed" : "HTTP request completed");
    }

    /**
     * 判断当前请求是否属于 Actuator 运维端点。
     *
     * @param request 当前 HTTP 请求对象
     * @return true 表示属于 /actuator 路径，不输出常规 HTTP 访问日志
     */
    private boolean isActuatorRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.equals("/actuator") || uri.startsWith("/actuator/"));
    }
}
