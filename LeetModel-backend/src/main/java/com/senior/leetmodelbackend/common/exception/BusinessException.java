package com.senior.leetmodelbackend.common.exception;

import com.senior.leetmodelbackend.pojo.enums.error.BaseErrorCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String message;


    public BusinessException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMsg();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BusinessException(BaseErrorCode errorCode, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BusinessException(BaseErrorCode errorCode, String msgOverride, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BusinessException(int code, String message, String message1) {
        this.code = code;
        this.message = message1;
    }

}
