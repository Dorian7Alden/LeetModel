package com.leetmodel.common.core.exception;

/**
 * 错误码接口 —— 所有模块的 ErrorCode 枚举必须实现此接口。
 *
 * <p>设计意图：各业务模块（user / team / problem 等）定义自己的错误码枚举，
 * 统一实现此接口，使得 {@link BusinessException} 和 {@link com.leetmodel.common.core.result.Result}
 * 不依赖任何具体枚举，遵循依赖倒置原则（DIP）。</p>
 *
 * <p>错误码按万位分段：
 * <pre>
 *     0xxxx  — 通用 / 系统级（common-core 定义）
 *     1xxxx  — 用户模块
 *     2xxxx  — 团队模块
 *     3xxxx  — 题目模块
 *     ...      后续模块递增
 * </pre>
 * </p>
 * @see ErrorCodeEnum
 * @see BusinessException
 */
public interface ErrorCode {

    /**
     * 获取业务错误码
     *
     * @return 错误码（20000 = 成功，见 A-BB-CC 编码规范）
     */
    int getCode();

    /**
     * 获取错误消息
     *
     * @return 面向用户的错误提示
     */
    String getMessage();
}
