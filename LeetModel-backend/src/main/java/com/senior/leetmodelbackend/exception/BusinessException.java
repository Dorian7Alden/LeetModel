package com.senior.leetmodelbackend.exception;


import com.senior.leetmodelbackend.entity.enums.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends Exception {

    private final int code;
    private final String msg;

    public BusinessException(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

}
