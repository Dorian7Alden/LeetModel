package com.leetmodel.assistant.tool;

/** 工具协议、校验、限制或执行失败的稳定内部异常。 */
public class AssistantToolException extends RuntimeException {

    private final String errorCode;

    public AssistantToolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AssistantToolException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 返回不包含下游地址和堆栈的稳定错误分类。 */
    public String getErrorCode() {
        return errorCode;
    }
}
