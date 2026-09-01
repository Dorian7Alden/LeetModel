package com.leetmodel.common.api.feign;

import com.leetmodel.common.core.telemetry.CorrelationContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Feign TraceId 透传拦截器 —— 从 MDC 读取 TraceId 并注入 Feign 请求头。
 *
 * <p>每次 Feign 调用时自动执行，将当前线程 MDC 中的 {@code X-Trace-Id}
 * 注入到下游服务的请求头中。下游的 {@code TraceIdServletFilter}
 * 会提取该 Header 并写入自己的 MDC，实现全链路追踪。</p>
 *
 * <p>放在 common-api 而非 common-core 的原因是：
 * common-core 不依赖 Feign，引入 {@link RequestInterceptor}
 * 会增加不必要的传递依赖。</p>
 */
@Component
public class TraceIdFeignInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFeignInterceptor.class);

    static final String TRACE_ID_HEADER = CorrelationContext.TRACE_ID_HEADER;

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
