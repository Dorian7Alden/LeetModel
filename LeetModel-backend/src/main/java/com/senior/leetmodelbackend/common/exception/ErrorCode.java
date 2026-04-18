package com.senior.leetmodelbackend.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    /**
     * 用户模块 (01)
     */
    USER_NOT_FOUND(40103, "该账号不存在"),
    USER_PASSWORD_WRONG(40104, "用户密码错误"),
    USER_ALREADY_EXISTS(40105, "该账号已存在"),

    /**
     * 赛事模块 (03)
     */
    COMPETITION_NO_COMPETITION(40301, "当前没有相关赛事"),

    /**
     * 临时末尾充数用的
     */
    NOTHING(0, "");

    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
