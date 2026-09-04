package com.leetmodel.gateway.handler;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Gateway 全局异常处理器：将 WebFlux 响应式异常统一转换为 {@link Result} JSON 响应。
 *
 * <p>在非阻塞网关管道中捕获未处理的路由异常、下游超时与网络中断，保证客户端获得契约对齐的错误返回。</p>
 */
@Configuration
@Order(-2)
public class JsonExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public JsonExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 统一处理 WebFlux 管道中的未捕获异常并写回 JSON 响应。
     *
     * @param exchange 响应式 HTTP 请求上下文，不能为 null
     * @param ex       未捕获的根因异常对象，不能为 null
     * @return Mono 表示写入完成的异步信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<?> result;
        HttpStatus httpStatus;

        // 按异常类型映射错误码
        if (ex instanceof NotFoundException) {
            // 路由未找到
            httpStatus = HttpStatus.NOT_FOUND;
            result = Result.fail(40400, "请求的接口不存在");
        } else if (ex instanceof ResponseStatusException rse) {
            // Spring 响应状态异常（通常由下游服务返回的非 2xx 触发）
            httpStatus = HttpStatus.valueOf(rse.getStatusCode().value());
            result = Result.fail(40000 + rse.getStatusCode().value(), "服务暂时不可用，请稍后再试");
        } else if (ex instanceof TimeoutException ||
                   (ex.getMessage() != null && ex.getMessage().contains("timeout"))) {
            // 超时（连接超时 / 响应超时）
            httpStatus = HttpStatus.GATEWAY_TIMEOUT;
            result = Result.fail(50400, "请求超时，请稍后再试");
            logFailure(ex, "TIMEOUT", 50400, false);
        } else if (ex instanceof java.net.ConnectException
                   || (ex.getMessage() != null && ex.getMessage().contains("Connection refused"))) {
            // 下游服务不可达
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            result = Result.fail(50300, "服务暂时不可用，请稍后再试");
            logFailure(ex, "UNAVAILABLE", 50300, false);
        } else {
            // 兜底：未预期的异常
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            result = Result.fail(50000, "网关内部错误");
            logFailure(ex, "UNEXPECTED", 50000, true);
        }

        response.setStatusCode(httpStatus);

        // 序列化为 JSON 并写入响应
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":50000,\"message\":\"网关内部错误\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private void logFailure(Throwable exception, String category, int errorCode, boolean error) {
        var event = error ? log.atError() : log.atWarn();
        event.setCause(exception)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.SYSTEM_FAILURE)
                .addKeyValue(LogFieldNames.ERROR_CODE, errorCode)
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, category)
                .log("Gateway request failed");
    }
}
