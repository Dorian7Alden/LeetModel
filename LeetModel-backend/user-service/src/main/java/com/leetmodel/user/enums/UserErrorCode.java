package com.leetmodel.user.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块业务错误码：BB=02（用户管理模块）。
 *
 * <p>编码规范：A-BB-CC 五段式
 * <ul>
 *   <li>A=4: 客户端错误/业务阻断</li>
 *   <li>BB=02: 用户模块</li>
 *   <li>CC: 具体错误序号，从 01 递增</li>
 * </ul>
 * </p>
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
    PASSWORD_OLD_INVALID(40206, "旧密码错误"),
    PASSWORD_SAME_AS_OLD(40207, "新密码不能与旧密码相同"),
    STORAGE_NOT_ENABLED(40208, "对象存储未启用，无法上传文件"),
    ROLE_NOT_FOUND(40209, "角色不存在"),
    ROLE_CODE_DUPLICATE(40210, "角色编码已存在"),
    PERMISSION_NOT_FOUND(40211, "权限不存在"),
    PERMISSION_CODE_DUPLICATE(40212, "权限编码已存在"),
    PERMISSION_IN_USE(40213, "权限已被角色使用，请先解除关联"),
    SYSTEM_ROLE_PROTECTED(40214, "系统预设角色不允许删除或修改编码"),
    DEFAULT_ROLE_NOT_FOUND(40215, "系统默认角色不存在"),
    ;

    private final int code;
    private final String message;
}
