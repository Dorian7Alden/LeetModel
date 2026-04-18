package com.senior.leetmodelbackend.common.exception;

import lombok.Getter;

/**
 * 系统异常
 */
@Getter
public class SystemException extends RuntimeException {

    private final Integer code;
    private final String msg;

    public SystemException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public SystemException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

}
