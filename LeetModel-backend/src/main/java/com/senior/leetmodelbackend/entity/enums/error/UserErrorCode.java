package com.senior.leetmodelbackend.entity.enums.error;

import lombok.Getter;

/**
 * 用户与认证模块状态码 (01模块)
 * 涵盖注册、登录、权限、角色设置等
 */
@Getter
public enum UserErrorCode implements BaseErrorCode {

    UNAUTHORIZED_TOKEN_MISSING(40100, "未登录或Token缺失，请重新登录"),
    UNAUTHORIZED_TOKEN_INVALID(40101, "Token已失效或解析失败，请重新登录"),
    PERMISSION_DENIED(40102, "权限不足，无法访问该资源"),
    
    USER_NOT_FOUND(40103, "用户不存在"),
    PASSWORD_INCORRECT(40104, "密码错误"),
    USER_ALREADY_EXISTS(40105, "该账号已存在"),
    
    VERIFICATION_CODE_INCORRECT(40106, "验证码错误或已过期"),
    VERIFICATION_CODE_FREQUENT(40107, "验证码获取过于频繁，请稍后再试"),
    
    USER_ROLE_NOT_SET(40108, "未设置备赛角色（建模手/编程手/论文手）"),
    USER_ACCOUNT_BANNED(40109, "您的账号已被封禁，请联系管理员");

    private final int code;
    private final String msg;

    UserErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
