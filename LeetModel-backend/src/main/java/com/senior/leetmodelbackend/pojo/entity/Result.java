package com.senior.leetmodelbackend.pojo.entity;


import com.senior.leetmodelbackend.common.exception.ResponseCode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Result<T> {

    private int code;
    private String msg;
    private T data;
    private Long timestamp;

    public Result() {
        this.timestamp = LocalDateTime.now().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = LocalDateTime.now().toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> success(String msg) {
        return new Result<>(200, msg, null);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(ResponseCode responseCode) {
        return new Result<>(responseCode.getCode(), responseCode.getMsg(), null);
    }

    public static <T> Result<T> error(ResponseCode responseCode, String msgOverride) {
        return new Result<>(responseCode.getCode(), msgOverride, null);
    }

}
