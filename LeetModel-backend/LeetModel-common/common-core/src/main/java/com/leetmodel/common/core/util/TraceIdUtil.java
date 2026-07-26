package com.leetmodel.common.core.util;

import org.slf4j.MDC;

/**
 * TraceId 工具类 —— 封装 SLF4J MDC 的读写操作。
 *
 * <p>用于微服务全链路日志追踪：
 * <ul>
 *   <li>网关层接收到请求后生成 traceId，调用 {@link #setTraceId(String)} 写入 MDC</li>
 *   <li>Feign / RestTemplate 拦截器从 MDC 读取 traceId 并注入到 HTTP Header</li>
 *   <li>下游服务从 Header 提取 traceId 后再次写入 MDC</li>
 *   <li>Logback 配置通过 {@code %X{traceId}} 在每条日志中自动输出 traceId</li>
 * </ul>
 * </p>
 *
 * <p>日志输出示例：</p>
 * <pre>{@code
 * 2026-07-26 10:30:15.123 [user-service,abc123def456] INFO  c.l.u.c.UserController - 用户登录成功
 * }</pre>
 *
 * @author LeetModel
 */
public final class TraceIdUtil {

    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
        // 工具类禁止实例化
    }

    /**
     * 将 traceId 写入当前线程的 MDC。
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 从当前线程的 MDC 中读取 traceId。
     *
     * @return traceId，未设置时返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 清除当前线程 MDC 中的 traceId。
     * 应在请求处理完成后（如 Filter / Interceptor 的 afterCompletion）调用，防止内存泄漏。
     */
    public static void removeTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }
}
