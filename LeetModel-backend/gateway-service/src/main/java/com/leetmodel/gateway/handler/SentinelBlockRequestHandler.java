package com.leetmodel.gateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel 网关限流与降级自定义处理器。
 *
 * <p>捕获触发流控阈值的请求，返回符合平台契约的统一 429 JSON 响应，维持 TraceId 关联事实并输出结构化容量保护日志。</p>
 */
@Component
public class SentinelBlockRequestHandler implements BlockRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(SentinelBlockRequestHandler.class);

    private final ObjectMapper objectMapper;

    public SentinelBlockRequestHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
        String traceId = resolveTraceId(exchange);
        logBlockEvent(exchange, traceId, ex);

        Result<Void> result = Result.fail(
                ErrorCodeEnum.RATE_LIMITED.getCode(),
                ErrorCodeEnum.RATE_LIMITED.getMessage()
        );

        String jsonBody = serializeResult(result);

        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CorrelationContext.TRACE_ID_HEADER, traceId)
                .bodyValue(jsonBody);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getResponse().getHeaders().getFirst(CorrelationContext.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        traceId = exchange.getRequest().getHeaders().getFirst(CorrelationContext.TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return CorrelationContext.newId();
    }

    private String serializeResult(Result<Void> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"code\":40004,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null,\"timestamp\":"
                    + System.currentTimeMillis() + "}";
        }
    }

    private void logBlockEvent(ServerWebExchange exchange, String traceId, Throwable ex) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeTemplate = route == null ? "UNMATCHED" : "route:" + route.getId();
        String method = exchange.getRequest().getMethod().name();

        log.atWarn()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.CAPACITY_PROTECTION_ACTIVATED)
                .addKeyValue(LogFieldNames.TRACE_ID, traceId)
                .addKeyValue(LogFieldNames.HTTP_METHOD, method)
                .addKeyValue(LogFieldNames.ROUTE_TEMPLATE, routeTemplate)
                .addKeyValue(LogFieldNames.STATUS_CODE, HttpStatus.TOO_MANY_REQUESTS.value())
                .addKeyValue(LogFieldNames.ERROR_CODE, ErrorCodeEnum.RATE_LIMITED.getCode())
                .log("Gateway request blocked by Sentinel capacity protection");
    }
}
