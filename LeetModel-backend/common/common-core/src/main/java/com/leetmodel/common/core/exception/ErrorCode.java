package com.leetmodel.common.core.exception;

/**
 * 错误码契约接口。
 *
 * <p>统一各模块错误码的编码规范与提取方法，解耦 BusinessException 与具体枚举实现。
 * 采用 A-BB-CC 五段式编码规范（A 错误等级、BB 业务模块编号、CC 错误序号）。</p>
 */
public interface ErrorCode {

    /**
     * 获取业务状态码。
     *
     * @return 符合 A-BB-CC 分段规范的整型业务状态码
     */
    int getCode();

    /**
     * 获取面向用户的错误提示文本。
     *
     * @return 错误提示描述信息
     */
    String getMessage();
}
