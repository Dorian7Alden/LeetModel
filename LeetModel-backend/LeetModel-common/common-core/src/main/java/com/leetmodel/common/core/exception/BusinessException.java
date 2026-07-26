package com.leetmodel.common.core.exception;

import lombok.Getter;

/**
 * 业务异常 —— 所有业务层抛出的异常类型。
 *
 * <p>继承自 {@link RuntimeException}，方便在事务管理中自动回滚。
 * 携带业务错误码和消息，由 {@link com.leetmodel.common.core.handler.GlobalExceptionHandler} 统一拦截转换为标准响应。</p>
 *
 * <p>两种构造方式：</p>
 * <ul>
 *   <li>直接传入 {@link ErrorCode} 实例，使用枚举中预定义的消息</li>
 *   <li>传入 ErrorCode + 自定义消息，用于需要附加上下文的场景（如 "用户名已被占用：dorian"）</li>
 * </ul>
 *
 * @author LeetModel
 * @see ErrorCode
 * @see ErrorCodeEnum
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 通过 ErrorCode 构造，使用枚举中预定义的消息。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 通过 ErrorCode + 自定义消息构造，用于需要附加业务上下文的场景。
     *
     * @param errorCode     错误码枚举（取其 code）
     * @param customMessage 自定义消息（覆盖枚举中的默认值）
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    /**
     * 条件断言：当 condition 为 true 时抛出业务异常。
     *
     * <p>语法糖，一行替代 if-throw 三行：</p>
     * <pre>{@code
     * BusinessException.throwIf(user == null, ErrorCodeEnum.USER_NOT_FOUND);
     * }</pre>
     *
     * @param condition 为 true 时抛异常
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new BusinessException(errorCode);
        }
    }
}
