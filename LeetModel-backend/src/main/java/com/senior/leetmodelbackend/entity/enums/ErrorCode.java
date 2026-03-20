package com.senior.leetmodelbackend.entity.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND(2001, "用户不存在"),
    USER_ALREADY_EXIST(2002, "用户已存在"),

    // 完整性异常
    EMAIL_BLANK(3001, "邮箱不能为空"),
    PASSWORD_BLANK(3002, "密码不能为空"),


    // 登录异常
    EMAIL_NOT_FOUND(4001, "邮箱不存在"),
    PASSWORD_ERROR(2003, "密码错误"),


    NOTING(4003, " noting");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}
