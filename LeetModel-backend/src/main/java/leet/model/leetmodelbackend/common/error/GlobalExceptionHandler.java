package leet.model.leetmodelbackend.common.error;

import leet.model.leetmodelbackend.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一把异常转换为标准响应结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.fail(exception.getResponseCode(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        String message = exception.getMessage();
        return Result.fail(ResponseCode.GLOBAL_PARAM_VALIDATION_ERROR,
                message == null || message.isBlank() ? ResponseCode.GLOBAL_PARAM_VALIDATION_ERROR.getMsg() : message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return Result.fail(ResponseCode.GLOBAL_SYSTEM_INTERNAL_ERROR);
    }
}