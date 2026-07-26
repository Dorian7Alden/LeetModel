package com.leetmodel.user.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块错误码 —— BB=02（用户管理模块）。
 *
 * <p>编码规范：A-BB-CC 五段式
 * <ul>
 *   <li>A=4：客户端错误/业务阻断</li>
 *   <li>BB=02：用户模块</li>
 *   <li>CC：具体错误序号，从 01 递增</li>
 * </ul>
 * </p>
 *
 * @author LeetModel
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // ==================== BB=02：用户模块 ====================

    USER_NOT_FOUND(40201, "用户不存在"),
    USERNAME_DUPLICATE(40202, "用户名已被占用"),
    PASSWORD_INVALID(40203, "密码错误"),
    TOKEN_EXPIRED(40204, "登录已过期，请重新登录"),
    ACCOUNT_DISABLED(40205, "账号已被禁用"),
    ;

    private final int code;
    private final String message;
}
