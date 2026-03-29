package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 第三方服务与AI模块状态码 (06模块)
 * 涵盖AI评审限制、大模型调用异常、邮件、OSS等服务异常
 */
@Getter
public enum ThirdPartyErrorCode implements BaseErrorCode {

    // 客户端/用户侧限制 (406xx)
    AI_INPUT_TOO_LONG(40601, "提交的评审内容字数超限，无法进行AI评审"),
    AI_QUOTA_EXCEEDED(40602, "您当前的AI评阅使用额度已耗尽，请稍后再试或获取更多额度"),

    // 服务端调用异常 (506xx)
    EMAIL_SEND_FAILED(50601, "邮件发送失败，服务器异常或网络超时"),
    AI_MODEL_CALL_FAILED(50602, "AI大模型服务调用失败，请稍后重试"),
    AI_RESPONSE_PARSE_ERROR(50603, "AI审核结果解析异常，无法生成多维度评分"),
    OSS_UPLOAD_FAILED(50604, "文件上传到云存储失败，请稍后重试");

    private final int code;
    private final String msg;

    ThirdPartyErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
