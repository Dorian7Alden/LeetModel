package com.leetmodel.common.core.result;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体 —— 所有 Controller 方法的返回类型。
 *
 * <p>状态码采用 A-BB-CC 五段式编码规范：
 * <ul>
 *   <li>A（万位）：2=成功 4=客户端错误/业务阻断 5=服务端错误/第三方异常</li>
 *   <li>BB（千位百位）：业务模块，00=全局通用 01=认证鉴权 02=用户 03=团队...</li>
 *   <li>CC（十位个位）：具体错误序号，从 01 递增</li>
 * </ul>
 * </p>
 *
 * <p>不依赖具体 ErrorCode 枚举，通过 {@link ErrorCode} 接口解耦，
 * 各模块可定义自己的错误码并传入 {@code Result.fail()}。</p>
 *
 * @param <T> 响应数据的类型
 * @author LeetModel
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码（A-BB-CC：20000=成功，4xxxx=客户端错误/业务阻断，5xxxx=服务端错误） */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 响应时间戳（毫秒） */
    private long timestamp;

    // ==================== 成功工厂方法 ====================

    /**
     * 操作成功（无返回数据）。
     */
    public static <T> Result<T> ok() {
        return new Result<>(20000, "success", null, System.currentTimeMillis());
    }

    /**
     * 操作成功（携带数据）。
     *
     * @param data 响应数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(20000, "success", data, System.currentTimeMillis());
    }

    /**
     * 操作成功（自定义消息 + 数据）。
     *
     * @param message 成功提示
     * @param data    响应数据
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(20000, message, data, System.currentTimeMillis());
    }

    // ==================== 失败工厂方法 ====================

    /**
     * 操作失败（通过 ErrorCode 传递错误码和消息）。
     *
     * @param errorCode 错误码枚举
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 操作失败（直接传递 code 和消息，用于不需要定义枚举的临时错误）。
     *
     * @param code    错误码
     * @param message 错误提示
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断是否为成功响应。
     * 2xxxx = 成功响应。
     */
    public boolean isSuccess() {
        return this.code >= 20000 && this.code < 30000;
    }
}
