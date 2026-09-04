package com.leetmodel.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局系统级通用错误码枚举。
 *
 * <p>覆盖全平台公用的执行成功、参数校验、资源定位及系统级未知故障，模块代号 BB 固定为 00。</p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum implements ErrorCode {

    SUCCESS(20000, "success"),                         // 业务执行成功
    PARAM_INVALID(40001, "参数校验失败"),                  // 客户端传参不符合校验约束
    NOT_FOUND(40002, "资源不存在"),                      // 请求的目标资源未找到
    METHOD_NOT_ALLOWED(40003, "请求方法不允许"),          // HTTP 请求方法不匹配
    RATE_LIMITED(40004, "请求过于频繁，请稍后再试"),         // 触发流控阈值限频
    SYSTEM_ERROR(50001, "系统内部错误"),                  // 服务端发生未受检异常兜底
    ;

    /** 业务状态码 */
    private final int code;

    /** 面向用户的错误提示文本 */
    private final String message;
}
