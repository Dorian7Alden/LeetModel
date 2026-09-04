package com.leetmodel.common.core.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全局统一接口响应体。
 *
 * <p>标准信封结构固定包含：code（业务状态码，20000 为成功）、message（提示文本）、data（数据负载）、timestamp（毫秒时间戳）。
 * 对外 HTTP 响应通常返回 HTTP 200，具体业务成败由内部 code 表达，防止网关层误判网络故障而触发盲目重试。</p>
 *
 * @param <T> 响应数据载荷的实际类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 业务状态码：20000 代表执行成功，4xxxx 为客户端或业务阻断，5xxxx 为系统故障 */
    private int code;

    /** 响应提示文本 */
    private String message;

    /** 业务数据负载，失败或无返回时为 null */
    private T data;

    /** 响应时间戳，毫秒值 */
    private long timestamp;

    // ==================== 成功工厂方法 ====================

    /**
     * 创建无返回数据的通用成功响应。
     *
     * @param <T> 响应数据类型
     * @return code 为 20000 且 data 为 null 的成功响应对象
     */
    public static <T> Result<T> ok() {
        return new Result<>(20000, "success", null, System.currentTimeMillis());
    }

    /**
     * 创建携带指定数据负载的成功响应。
     *
     * @param data 业务数据载荷，允许为 null
     * @param <T>  响应数据类型
     * @return code 为 20000 且包含数据载荷的成功响应对象
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(20000, "success", data, System.currentTimeMillis());
    }

    /**
     * 创建携带自定义提示消息与数据负载的成功响应。
     *
     * @param message 成功提示文本，不能为空
     * @param data    业务数据载荷，允许为 null
     * @param <T>     响应数据类型
     * @return 包含指定提示消息与数据载荷的成功响应对象
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(20000, message, data, System.currentTimeMillis());
    }

    // ==================== 失败工厂方法 ====================

    /**
     * 根据标准错误码契约创建失败响应。
     *
     * @param errorCode 错误码契约实例，不能为空
     * @param <T>       响应数据类型
     * @return 包含错误码对应状态码和描述的失败响应对象，data 固定为 null
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 根据指定状态码与错误信息创建失败响应。
     *
     * @param code    整型业务状态码，必须非 20000
     * @param message 错误提示文本，不能为空
     * @param <T>     响应数据类型
     * @return 包含指定状态码与描述的失败响应对象，data 固定为 null
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断当前响应是否为业务成功状态。
     *
     * <p>标注 @JsonIgnore 避免被 Jackson 自动提取为多余字段 success 导出。</p>
     *
     * @return true 表示业务执行成功（code 在 20000 至 29999 范围内），false 表示失败
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code >= 20000 && this.code < 30000;
    }
}
