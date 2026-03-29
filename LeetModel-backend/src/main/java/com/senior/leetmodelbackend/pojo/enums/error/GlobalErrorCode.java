package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 全局通用状态码 (00模块)
 * 涵盖成功状态、通用客户端错误、服务器内部异常等
 */
@Getter
public enum GlobalErrorCode implements BaseErrorCode {

    // 成功状态 (2xxxx)
    SUCCESS(20000, "操作成功"),

    // 客户端通用错误 (400xx)
    CLIENT_ERROR_COMMON(40000, "通用请求错误"),
    PARAM_VALIDATION_ERROR(40001, "参数校验不通过"),
    RESOURCE_NOT_FOUND(40002, "请求资源不存在"),
    FILE_SIZE_EXCEEDED(40003, "文件大小超限"),
    FILE_TYPE_UNSUPPORTED(40004, "不支持的文件类型"),

    // 服务端内部通用异常 (500xx)
    SYSTEM_INTERNAL_ERROR(50000, "服务器内部异常，请稍后重试");

    private final int code;
    private final String msg;

    GlobalErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
