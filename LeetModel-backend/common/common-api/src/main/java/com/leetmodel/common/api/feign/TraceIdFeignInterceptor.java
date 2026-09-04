package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Feign 全链路追踪透传拦截器。
 *
 * <p>从当前线程 MDC 提取 X-Trace-Id 与 X-Operation-Id 请求头注入下游请求，
 * 并在注入前清理手工伪造的不受信任头部，实现跨微服务 Trace 透传。</p>
 */
@Component
public class TraceIdFeignInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFeignInterceptor.class);

    /** 跨服务透传的标准 HTTP Header */
    static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

    /**
     * 拦截 Feign 请求模板，注入当前受信任的追踪与治理标识。
     *
     * @param template 待发送的 Feign 请求模板，不能为空
     */
    @Override
    public void apply(RequestTemplate template) {
        // 不信任 Feign 契约参数或手工预置的内部关联头。
        template.removeHeader(TRACE_ID_HEADER);
        template.removeHeader(CorrelationContext.OPERATION_ID_HEADER);

        String traceId = CorrelationContext.traceId();
        if (traceId != null && !traceId.isBlank()) {
            template.header(TRACE_ID_HEADER, traceId);
            log.debug("TraceId 已注入 Feign 请求头");
        }
        String operationId = CorrelationContext.operationId();
        if (operationId != null) {
            template.header(CorrelationContext.OPERATION_ID_HEADER, operationId);
        }
    }
}
