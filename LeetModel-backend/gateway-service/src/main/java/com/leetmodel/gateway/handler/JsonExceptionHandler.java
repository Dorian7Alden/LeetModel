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
 * Gateway 全局异常处理器 —— 将 WebFlux 异常转换为统一的 {@link Result} JSON 响应。
 *
 * <p>为什么需要这个？
 * <ul>
 *   <li>Gateway 基于 Netty + WebFlux（响应式），不走 Servlet 容器，
 *       因此 common-core 中的 {@code @RestControllerAdvice} 对它无效</li>
 *   <li>没有此处理器时，路由失败/下游宕机/超时会返回 Spring 默认的 HTML/JSON 错误，
 *       破坏前端对统一 {@code Result} 格式的约定</li>
 * </ul>
 * </p>
 *
 * <h3>核心设计思考与技术要点</h3>
 * <ul>
 *   <li><b>WebFlux vs Servlet 异常处理</b>：Servlet 用 {@code @RestControllerAdvice + @ExceptionHandler}，
 *       WebFlux 用 {@code ErrorWebExceptionHandler}。根本原因是两者的请求处理模型不同
 *       （同步阻塞 vs 响应式非阻塞），全局通知机制不互通。</li>
 *   <li><b>{@code @Order(-1)}</b>：Spring Boot 默认注册的
 *       {@code DefaultErrorWebExceptionHandler} 优先级是 {@code -1} 的前一级，
 *       我们的实现用 {@code -2} 确保优先于默认处理器，覆盖其行为。</li>
 *   <li><b>{@code DataBuffer} 内存管理</b>：响应式 I/O 使用 Netty 的引用计数 ByteBuf，
 *       写入响应后 Spring 自动释放，无需手动 {@code release()}。</li>
 *   <li><b>{@code response.writeWith()} vs {@code response.writeAndFlushWith()}</b>：
 *       前者返回 Mono&lt;Void&gt; 表示写入完成信号（数据可能还在 OS 缓冲区），
 *       后者返回 Flux 支持流式写入。单个 JSON body 用前者即可。</li>
 * </ul>
 */
@Configuration
@Order(-2)
public class JsonExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public JsonExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
