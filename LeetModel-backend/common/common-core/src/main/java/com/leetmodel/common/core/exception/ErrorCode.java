package com.leetmodel.common.core.exception;

/**
 * 错误码接口 —— 所有模块的 ErrorCode 枚举必须实现此接口。
 *
 * <p>设计意图：各业务模块定义自己的错误码枚举，
 * 统一实现此接口，使得 {@link BusinessException} 和 {@link com.leetmodel.common.core.result.Result}
 * 不依赖任何具体枚举，遵循依赖倒置原则。</p>
 *
 * <p>错误码采用 A-BB-CC 五位编码：
 * <pre>
 *     A   — 响应场景，2 表示成功，4 表示客户端错误，5 表示服务端错误
 *     BB  — 业务模块，00 表示全局，01 表示鉴权，02 表示用户
 *     CC  — 模块内具体场景序号，从 01 递增
 * </pre>
 * 示例：40209 表示用户模块的角色不存在错误。</p>
 * @see ErrorCodeEnum
 * @see BusinessException
 */
public interface ErrorCode {

    /**
     * 获取业务错误码
     *
     * @return 符合 A-BB-CC 规范的五位业务状态码
     */
    int getCode();

    /**
     * 获取错误消息
     *
     * @return 面向用户的错误提示
     */
    String getMessage();
}
