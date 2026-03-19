package com.senior.leetmodelbackend.exception;

import com.senior.leetmodelbackend.entity.pojo.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截POST请求JSON参数校验异常（@Valid/@Validated校验失败）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 获取绑定结果，解析具体的字段错误
        BindingResult bindingResult = e.getBindingResult();
        // 拼接错误信息（比如："email:邮箱不能为空"）
        StringBuilder errorMsg = new StringBuilder();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            // fieldError.getField()：错误的参数名（如email）
            // fieldError.getDefaultMessage()：@NotBlank等注解的message值
            errorMsg.append(fieldError.getField()).append(":").append(fieldError.getDefaultMessage()).append(";");
        }
        // 返回参数错误提示（截取最后一个分号，避免多余）
        String msg = !errorMsg.isEmpty() ? errorMsg.substring(0, errorMsg.length() - 1) : "参数校验失败";
        return Result.error(400, msg);
    }

    /**
     * 拦截GET请求参数校验异常（@RequestParam/@PathVariable校验失败）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        StringBuilder errorMsg = new StringBuilder();
        for (ConstraintViolation<?> violation : violations) {
            // 获取参数名和错误提示
            String paramName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errorMsg.append(paramName).append(":").append(message).append(";");
        }
        String msg = errorMsg.length() > 0 ? errorMsg.substring(0, errorMsg.length() - 1) : "参数校验失败";
        return Result.error(400, msg);
    }

    /**
     * 拦截其他系统异常（兜底处理）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 生产环境建议记录日志，不要返回具体异常信息
        return Result.error(500, "系统异常，请稍后重试");
    }


}
