package com.leetmodel.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.leetmodel.common.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证鉴权异常拦截处理器。
 *
 * <p>拦截 Sa-Token 抛出的未登录、权限不足及角色不匹配异常，将其转换为标准统一 Result 响应，
 * 并准确映射为 HTTP 401 与 403 状态码。与 common-core 的 GlobalExceptionHandler 互补配合。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    /**
     * 拦截未登录异常并映射为 HTTP 401。
     *
     * @param e 包含未登录原因的 NotLoginException 异常对象
     * @return 状态码为 40101 的错误响应对象
     */
    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleNotLogin(NotLoginException e) {
        return Result.fail(40101, "请先登录");
    }

    /**
     * 拦截权限不匹配异常并映射为 HTTP 403。
     *
     * @param e 权限缺失的 NotPermissionException 异常对象
     * @return 状态码为 40103 的错误响应对象
     */
    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleNotPermission(NotPermissionException e) {
        return Result.fail(40103, "权限不足");
    }

    /**
     * 拦截角色不满足异常并映射为 HTTP 403。
     *
     * @param e 角色不足的 NotRoleException 异常对象
     * @return 状态码为 40104 的错误响应对象
     */
    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleNotRole(NotRoleException e) {
        return Result.fail(40104, "角色不满足");
    }
}
