package com.leetmodel.common.core.util;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCode;

/**
 * 参数断言工具类。
 *
 * <p>提供前置条件与参数合法性校验语法糖，断言失败统一抛出 BusinessException，由 GlobalExceptionHandler 收敛。</p>
 */
public final class AssertUtil {

    private AssertUtil() {
        // 工具类禁止实例化
    }

    /**
     * 断言指定对象不能为 null。
     *
     * @param obj       待判定的对象
     * @param errorCode 对象为 null 时抛出的业务错误码，不能为空
     * @throws BusinessException 当判定对象为 null 时抛出
     */
    public static void notNull(Object obj, ErrorCode errorCode) {
        if (obj == null) {
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 断言指定对象不能为 null，并支持自定义覆盖错误提示。
     *
     * @param obj       待判定的对象
     * @param errorCode 目标业务错误码，不能为空
     * @param detail    自定义覆盖的错误提示描述，不能为空
     * @throws BusinessException 当判定对象为 null 时抛出
     */
    public static void notNull(Object obj, ErrorCode errorCode, String detail) {
        if (obj == null) {
            throw new BusinessException(errorCode, detail);
        }
    }

    /**
     * 断言指定字符串不能为空白串。
     *
     * @param str       待检查的字符串
     * @param errorCode 字符串为 null、空串或全空白时抛出的业务错误码，不能为空
     * @throws BusinessException 当字符串为空白时抛出
     */
    public static void notBlank(String str, ErrorCode errorCode) {
        if (str == null || str.isBlank()) {
            throw new BusinessException(errorCode);
        }
    }

    /**
     * 断言条件表达式必须为 true。
     *
     * @param condition 待判定的布尔条件表达式
     * @param errorCode 条件为 false 时抛出的业务错误码，不能为空
     * @throws BusinessException 当判定条件为 false 时抛出
     */
    public static void isTrue(boolean condition, ErrorCode errorCode) {
        if (!condition) {
            throw new BusinessException(errorCode);
        }
    }
}
