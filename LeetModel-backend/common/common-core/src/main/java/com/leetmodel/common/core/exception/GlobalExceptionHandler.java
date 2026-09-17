package com.leetmodel.common.core.exception;

import com.leetmodel.common.core.logging.LogEventCodes;
import com.leetmodel.common.core.logging.LogFieldNames;
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
 * 全局 Controller 异常拦截处理器。
 *
 * <p>统一捕获业务异常、入参校验异常、请求格式错误及未知系统异常，转换为统一 Result 响应信封。
 * 业务告警记录 WARN 且不打印冗余堆栈；系统级严重故障记录 ERROR 并保留完整异常堆栈。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截自定义业务异常并转换为错误响应。
     *
     * @param e 捕获的业务异常对象
     * @return 包含业务错误码与提示信息的 Result 响应对象
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.atWarn()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.REQUEST_REJECTED)
                .addKeyValue(LogFieldNames.ERROR_CODE, e.getCode())
                .addKeyValue(LogFieldNames.EXCEPTION_TYPE, e.getClass().getName())
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "BUSINESS")
                .log("Business request rejected");
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 拦截 Controller 实体参数 @Valid 校验失败异常。
     *
     * @param e 包含字段校验失败详情的异常对象
     * @return 包含所有字段校验失败提示的 Result 响应对象，code 为 40001
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        logRequestRejected(e, "VALIDATION");
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 拦截路径变量与单参数约束校验异常。
     *
     * @param e 包含约束违规明细的异常对象
     * @return 包含去重后违规描述的 Result 响应对象，code 为 40001
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        logRequestRejected(e, "VALIDATION");
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 拦截表单或查询参数对象数据绑定失败异常。
     *
     * @param e 数据绑定异常对象
     * @return 拼接了绑定失败字段信息的 Result 响应对象，code 为 40001
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        logRequestRejected(e, "BINDING");
        return Result.fail(ErrorCodeEnum.PARAM_INVALID.getCode(), msg);
    }

    /**
     * 拦截参数类型不匹配、缺失必填参数及 JSON 解析格式错误。
     *
     * @param e 格式或类型转换异常对象
     * @return 统一返回参数不合规的 Result 响应对象，code 为 40001
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public Result<?> handleRequestFormatException(Exception e) {
        logRequestRejected(e, "FORMAT");
        return Result.fail(ErrorCodeEnum.PARAM_INVALID);
    }

    /**
     * 兜底拦截未显式处理的系统级未知异常。
     *
     * @param e 未捕获的系统异常对象
     * @return 返回中立的系统内部错误 Result 响应对象，对外隐藏内部异常堆栈
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.atError()
                .setCause(e)
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.SYSTEM_FAILURE)
                .addKeyValue(LogFieldNames.ERROR_CODE, ErrorCodeEnum.SYSTEM_ERROR.getCode())
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, "UNEXPECTED")
                .log("Unhandled application failure");
        return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
    }

    /**
     * 记录请求被拒绝的统一结构化告警日志。
     *
     * @param exception 触发拒绝的异常对象
     * @param category  失败分类标签，如 VALIDATION、BINDING、FORMAT
     */
    private void logRequestRejected(Exception exception, String category) {
        log.atWarn()
                .addKeyValue(LogFieldNames.EVENT_CODE, LogEventCodes.REQUEST_REJECTED)
                .addKeyValue(LogFieldNames.ERROR_CODE, ErrorCodeEnum.PARAM_INVALID.getCode())
                .addKeyValue(LogFieldNames.EXCEPTION_TYPE, exception.getClass().getName())
                .addKeyValue(LogFieldNames.FAILURE_CATEGORY, category)
                .log("Request rejected");
    }
}
