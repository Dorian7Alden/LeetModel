package com.leetmodel.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.leetmodel.common.core.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证鉴权异常处理器 —— 将 Sa-Token 异常转换为统一的 {@link Result} 响应。
 *
 * <p>错误码 BB=01（认证鉴权模块）：
 * <ul>
 *   <li>40101 — 未登录（Token 过期、格式错误、在黑名单中）</li>
 *   <li>40103 — 权限不足（@SaCheckPermission 校验失败）</li>
 *   <li>40104 — 角色不满足（@SaCheckRole 校验失败）</li>
 * </ul>
 * </p>
 *
 * <p>与 common-core 的 {@code GlobalExceptionHandler} 互补：
 * common-core 处理业务异常，common-security 处理认证鉴权异常。</p>
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * 未登录异常 → HTTP 401。
     * 触发场景：Token 过期、Token 格式错误、Token 在黑名单中。
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleNotLogin(NotLoginException e) {
        return Result.fail(40101, "请先登录");
    }

    /**
     * 无权限异常 → HTTP 403。
     * 触发场景：{@code @SaCheckPermission} 校验失败。
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleNotPermission(NotPermissionException e) {
        return Result.fail(40103, "权限不足");
    }

    /**
     * 角色不匹配异常 → HTTP 403。
     * 触发场景：{@code @SaCheckRole} 校验失败。
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleNotRole(NotRoleException e) {
        return Result.fail(40104, "角色不满足");
    }
}
