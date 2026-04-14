package com.senior.leetmodelbackend.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(40103, "该账号不存在"),
    USER_PASSWORD_WRONG(40104, "用户密码错误"),
    USER_ALREADY_EXISTS(40105, "该账号已存在");

    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
