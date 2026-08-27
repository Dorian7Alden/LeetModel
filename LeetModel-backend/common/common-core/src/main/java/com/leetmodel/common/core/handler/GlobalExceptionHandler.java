package com.leetmodel.common.core.handler;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 拦截所有 Controller 抛出的异常，转换为统一的 {@link Result} 响应。
 *
 * <p>引入 common-core 的微服务自动获得全局异常处理能力，无需额外配置。</p>
 *
 * <p>处理优先级：按方法声明顺序匹配，先精确后模糊。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 —— 返回对应的业务错误码和消息。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 —— 当 @Valid 校验失败时触发。
     * 提取所有字段校验失败信息，拼接为一个可读字符串。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", msg);
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 方法参数约束异常 —— 处理 @PathVariable、@RequestParam 上的校验注解。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        log.warn("[方法参数校验失败] {}", msg);
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 查询对象绑定异常。
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数绑定失败] {}", msg);
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 参数类型、必填参数和 JSON 结构错误。
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public Result<?> handleRequestFormatException(Exception e) {
        log.warn("[请求格式错误] type={}, message={}", e.getClass().getSimpleName(), e.getMessage());
        return Result.fail(ErrorCodeEnum.PARAM_INVALID);
    }

    /**
     * 兜底异常 —— 处理未被上面 handler 捕获的所有异常。
     * 返回通用系统错误，不向客户端暴露异常细节。
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("[系统异常] type={}, message={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }
}
