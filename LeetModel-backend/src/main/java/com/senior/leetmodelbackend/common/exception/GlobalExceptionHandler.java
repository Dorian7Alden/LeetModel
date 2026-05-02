package com.senior.leetmodelbackend.common.exception;

import com.senior.leetmodelbackend.pojo.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：拦截所有 @RestController 的异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // 业务异常：只打印警告日志，不打印堆栈
        log.warn("业务异常：code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }
    /**
     * 捕获系统异常
     */
    @ExceptionHandler(SystemException.class)
    public Result<Void> handleSystemException(SystemException e) {
        // 系统异常：打印错误日志 + 堆栈，方便排查
        log.error("系统异常：code={}, msg={}", e.getCode(), e.getMessage(), e);
        return Result.error(e.getCode(), "系统繁忙，请稍后再试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 生产环境建议记录日志，不要返回具体异常信息
        log.error("服务器未知异常:", e);
        return Result.error(ResponseCode.SYSTEM_INTERNAL_ERROR);
    }

}
