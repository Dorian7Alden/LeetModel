package com.senior.leetmodelbackend.pojo.entity;


import com.senior.leetmodelbackend.pojo.enums.error.BaseErrorCode;
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

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("success");
        return result;
    }

    public static <T> Result<T> success(String msg) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        return result;
    }



    public static <T> Result<T> success(String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    public static <T> Result<T> error(BaseErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMsg(errorCode.getMsg());
        return result;
    }

    public static <T> Result<T> error(BaseErrorCode errorCode, String msgOverride) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMsg(msgOverride);
        return result;
    }

}
