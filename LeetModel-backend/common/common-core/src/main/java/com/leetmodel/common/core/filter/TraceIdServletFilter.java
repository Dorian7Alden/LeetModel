package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.core.telemetry.CorrelationSnapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet TraceId 过滤器 —— 从请求头提取 X-Trace-Id 并写入 MDC。
 *
 * <p>职责：从 Gateway 转发的请求中提取 {@code X-Trace-Id} Header，
 * 通过 {@link CorrelationContext} 建立可恢复 MDC 作用域，
 * 请求结束后恢复先前上下文。</p>
 *
 * <p>设计：
 * <ul>
 *   <li>只信任内网 Gateway/Feign 传来的合法标识；直连或非法输入生成本地 traceId</li>
 *   <li>{@code @Order(HIGHEST_PRECEDENCE)} 确保在其他过滤器和拦截器之前执行</li>
 *   <li>可恢复作用域保证 MDC 不覆盖容器原上下文，也不在线程池中泄漏</li>
 *   <li>响应头中也注入 {@code X-Trace-Id}，方便调试时从浏览器/前端日志定位</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdServletFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdServletFilter.class);

    static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

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
        CorrelationSnapshot snapshot = CorrelationSnapshot.EMPTY
                .withTraceId(traceId)
                .withOperationId(operationId);

        try (CorrelationContext.Scope ignored = CorrelationContext.open(snapshot)) {
            log.debug("HTTP 关联上下文已建立: traceId={}", traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        }
    }
}
