package com.leetmodel.common.core.util;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCode;

/**
 * 断言工具类 —— 参数校验和前置条件判断的语法糖。
 *
 * <p>一行断言替代传统的 if-throw 三行代码：</p>
 * <pre>{@code
 * // 传统写法（3行）
 * if (user == null) {
 *     throw new BusinessException(ErrorCodeEnum.USER_NOT_FOUND);
 * }
 *
 * // 使用 AssertUtil（1行）
 * AssertUtil.notNull(user, ErrorCodeEnum.USER_NOT_FOUND);
 * }</pre>
 *
 * <p>所有方法失败时抛出 {@link BusinessException}，由 {@link com.leetmodel.common.core.exception.GlobalExceptionHandler} 统一拦截。</p>
 * @see BusinessException
 * @see ErrorCode
 */
public final class AssertUtil {

    private AssertUtil() {
        // 工具类禁止实例化
    }

    /**
     * 断言对象非 null。
     *
     * @param obj       待检查的对象
     * @param errorCode 为 null 时使用的错误码
     * @throws BusinessException 当 obj 为 null 时抛出
     */
    public static void notNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 断言对象非 null，失败时使用自定义消息覆盖错误码的默认消息。
     *
     * @param obj    待检查的对象
     * @param errorCode 错误码（取其 code）
     * @param detail 自定义错误消息（如 "用户 ID=123 不存在"）
     * @throws BusinessException 当 obj 为 null 时抛出
     */
    public static void notNull(Object obj, ErrorCode errorCode, String detail) {
        if (obj == null) {
            throw new BusinessException(errorCode, detail);
        }
    }

    /**
     * 断言字符串非空（null、""、全空格均视为空）。
     *
     * @param str       待检查的字符串
     * @param errorCode 为空时使用的错误码
     * @throws BusinessException 当 str 为空时抛出
     */
    public static void notBlank(String str, ErrorCode errorCode) {
        if (str == null || str.isBlank()) {
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 断言条件为 true。
     *
     * @param condition 条件表达式
     * @param errorCode 条件为 false 时使用的错误码
     * @throws BusinessException 当 condition 为 false 时抛出
     */
    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }
}
