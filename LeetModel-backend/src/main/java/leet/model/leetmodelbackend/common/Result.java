package leet.model.leetmodelbackend.common;

import leet.model.leetmodelbackend.common.error.ResponseCode;

/**
 * 统一响应体。
 *
 * @param code 响应码。
 * @param msg 响应消息。
 * @param data 响应数据。
 * @param timestamp 响应时间戳。
 * @param <T> 响应数据类型。
 */
public record Result<T>(Integer code, String msg, T data, Long timestamp) {

    public static <T> Result<T> success(T data) {
        return success(data, ResponseCode.SUCCESS.getMsg());
    }

    public static <T> Result<T> success(T data, String msg) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), msg, data, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ResponseCode responseCode) {
        return fail(responseCode, responseCode.getMsg());
    }

    public static <T> Result<T> fail(ResponseCode responseCode, String msg) {
        return new Result<>(responseCode.getCode(), msg, null, System.currentTimeMillis());
    }
}