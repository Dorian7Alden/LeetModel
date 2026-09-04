package com.leetmodel.common.core.exception;

import lombok.Getter;

/**
 * 业务运行时异常基类。
 *
 * <p>继承 RuntimeException 以支持 Spring 声明式事务自动回滚；携带业务状态码 code 与提示 message，
 * 由 GlobalExceptionHandler 统一拦截并收敛为标准 Result 失败响应。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /**
     * 基于预定义错误码契约构建业务异常。
     *
     * @param errorCode 错误码契约实例，不能为空
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 基于错误码与自定义提示信息构建业务异常。
     *
     * @param errorCode     错误码契约实例，用于获取状态码
     * @param customMessage 覆盖默认提示的自定义错误描述文本，不能为空
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.code = errorCode.getCode();
    }

    /**
     * 条件断言：当 condition 为 true 时抛出业务异常。
     *
     * @param condition 判定条件表达式
     * @param errorCode 条件成立时抛出的目标错误码，不能为空
     * @throws BusinessException 当 condition 为 true 时抛出
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new BusinessException(errorCode);
        }
    }
}
