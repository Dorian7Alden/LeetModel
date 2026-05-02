package com.senior.leetmodelbackend.common.exception;

import lombok.Getter;

@Getter
public enum ResponseCode {

    /**
     * 全局通用状态码 (00模块)
     */
    SUCCESS(20000, "操作成功"),
    SYSTEM_INTERNAL_ERROR(50000, "服务器内部异常，请稍后重试"),
    PARAM_VALIDATION_ERROR(40001, "参数校验不通过"),

    /**
     * 用户模块 (01)
     */
    UNAUTHORIZED_TOKEN_MISSING(40100, "未登录或Token缺失，请重新登录"),
    UNAUTHORIZED_TOKEN_INVALID(40101, "Token已失效或解析失败，请重新登录"),
    USER_NOT_FOUND(40103, "用户不存在"),
    USER_PASSWORD_WRONG(40104, "密码错误"),
    USER_ALREADY_EXISTS(40105, "该账号已存在"),
    VERIFICATION_CODE_INCORRECT(40106, "验证码错误或已过期"),
    VERIFICATION_CODE_FREQUENT(40107, "验证码获取过于频繁，请稍后再试"),

    /**
     * 赛事模块 (03)
     */
    COMPETITION_NO_COMPETITION(40301, "当前没有相关赛事"),

    /**
     * 鉴权验证码模块 (05)
     */
    AUTH_EMAIL_SEND_FAILED(50101, "验证码邮件发送失败，请稍后重试"),
    AUTH_EMAIL_CODE_CACHE_FAILED(50102, "验证码缓存失败，请稍后重试"),
    AUTH_EMAIL_SEND_TOO_FREQUENT(50103, "验证码发送过于频繁，请稍后再试"),

    /**
     * 第三方服务与AI模块 (06)
     */
    EMAIL_SEND_FAILED(50601, "邮件发送失败，服务器异常或网络超时");


    private final Integer code;
    private final String msg;

    ResponseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
