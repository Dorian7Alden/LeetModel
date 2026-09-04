package com.leetmodel.common.core.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应体 —— 全平台所有对外 HTTP 接口与内部 Feign 调用的标准信封封装。
 *
 * <p>契约固定包含四个核心字段：状态码（code）、描述文本（message）、数据负载（data）与时间戳（timestamp）。</p>
 *
 * <h3>核心设计思考与面试考点</h3>
 * <ul>
 *   <li><b>为什么统一返回 HTTP 200 而不是直接使用 HTTP 4xx/5xx 状态码表达业务失败？</b><br/>
 *       1. 职责分层：HTTP 状态码表达的是“传输层/网络层”通信状态，而业务状态码表达的是“领域应用层”执行结果；<br/>
 *       2. 网关保护：若业务错误直接返回 HTTP 500，会触发微服务网关（Spring Cloud Gateway）或云负载均衡器的
 *          重试风暴与熔断器误触发；<br/>
 *       3. 客户端体验：统一信封结构便于前端网络拦截器（Axios）全局提取 message 并弹出 Toast 提示。</li>
 *   <li><b>状态码分段规范（A-BB-CC 结构）：</b><br/>
 *       采用五位分段标准：首位 A 表达错误严重等级（2 成功、4 业务阻断/参数不合规、5 系统严重故障）；
 *       中间两位 BB 表达业务领域代号（00 通用、01 用户、02 组队、03 题库等）；后两位 CC 表达领域内具体错误编号。</li>
 *   <li><b>为什么 isSuccess() 要标注 @JsonIgnore？</b><br/>
 *       若不标注，Jackson 默认会将符合 JavaBean 规范的 isXxx() 方法自动识别并序列化为 {@code "success": true/false}，
 *       导致网络传输体出现多余非契约字段，违背微服务严格传输契约原则。</li>
 * </ul>
 *
 * @param <T> 响应数据的实际类型
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
    @JsonIgnore
    public boolean isSuccess() {
        return this.code >= 20000 && this.code < 30000;
    }
}
