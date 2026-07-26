package com.leetmodel.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码枚举 —— 适用于所有模块的系统级错误。
 *
 * <p>编码规范：A-BB-CC 五段式
 * <ul>
 *   <li>A（万位）：2=成功 4=客户端错误/业务阻断 5=服务端错误/第三方异常</li>
 *   <li>BB（千位百位）：业务模块，00=全局通用</li>
 *   <li>CC（十位个位）：具体错误序号，从 01 递增</li>
 * </ul>
 * </p>
 *
 * <p>各业务模块定义自己的 ErrorCode 枚举实现 {@link ErrorCode} 接口，使用对应号段：
 * 01=认证鉴权 02=用户 03=团队 04=题目...</p>
 *
 * @author LeetModel
 * @see ErrorCode
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum implements ErrorCode {

    // ==================== BB=00：全局通用 ====================

    SUCCESS(20000, "success"),
    PARAM_INVALID(40001, "参数校验失败"),
    NOT_FOUND(40002, "资源不存在"),
    METHOD_NOT_ALLOWED(40003, "请求方法不允许"),
    RATE_LIMITED(40004, "请求过于频繁，请稍后再试"),
    SYSTEM_ERROR(50001, "系统内部错误"),
    ;

    /** 业务状态码（A-BB-CC 五段式） */
    private final int code;

    /** 面向用户的错误提示 */
    private final String message;
}
