package com.leetmodel.common.core.filter;

import com.leetmodel.common.core.util.TraceIdUtil;
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
 * 调用 {@link TraceIdUtil#setTraceId(String)} 写入 MDC，
 * 请求结束后自动清理。</p>
 *
 * <p>设计：
 * <ul>
 *   <li>本过滤器只提取，不生成 —— TraceId 的唯一来源是 Gateway，
 *       绕过 Gateway 直接访问服务时日志显示 N/A，属于预期行为</li>
 *   <li>{@code @Order(HIGHEST_PRECEDENCE)} 确保在其他过滤器和拦截器之前执行</li>
 *   <li>{@code finally} 块保证 MDC 一定被清理，防止线程池复用时串扰</li>
 *   <li>响应头中也注入 {@code X-Trace-Id}，方便调试时从浏览器/前端日志定位</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdServletFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdServletFilter.class);

    static final String TRACE_ID_HEADER = TraceIdUtil.TRACE_ID_HEADER;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            TraceIdUtil.setTraceId(traceId);
            log.debug("TraceId 已从请求头提取: {}", traceId);
        }

        try {
            // 响应头也带上，方便前端/H5 排查
            if (traceId != null) {
                response.setHeader(TRACE_ID_HEADER, traceId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TraceIdUtil.removeTraceId();
        }
    }
}
