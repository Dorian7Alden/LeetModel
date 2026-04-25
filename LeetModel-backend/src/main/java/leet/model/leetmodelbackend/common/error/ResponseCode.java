package leet.model.leetmodelbackend.common.error;

import lombok.Getter;

/**
 * 统一响应码定义。
 */
@Getter
public enum ResponseCode {

    SUCCESS(200, "success"),

    GLOBAL_PARAM_VALIDATION_ERROR(40001, "参数校验不通过"),
    GLOBAL_SYSTEM_INTERNAL_ERROR(50000, "服务器内部异常，请稍后重试"),

    AUTH_UNAUTHORIZED(40101, "未登录或令牌缺失"),
    AUTH_INVALID_TOKEN(40102, "令牌无效"),
    AUTH_TOKEN_EXPIRED(40103, "令牌已过期"),
    AUTH_FORBIDDEN(40301, "无权限访问");

    private final Integer code;

    private final String msg;

    ResponseCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}