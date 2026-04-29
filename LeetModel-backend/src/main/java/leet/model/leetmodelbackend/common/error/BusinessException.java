package leet.model.leetmodelbackend.common.error;

import lombok.Getter;

/**
 * 业务异常，用于承载统一错误码和错误信息。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final ResponseCode responseCode;

    /** 详细错误信息，默认取 responseCode 内置消息 */
    private final String detailMessage;

    /** 使用 responseCode 内置消息构造 */
    public BusinessException(ResponseCode responseCode) {
        this(responseCode, responseCode.getMsg());
    }

    /** 使用自定义消息构造，允许前端展示更精确的错误信息 */
    public BusinessException(ResponseCode responseCode, String detailMessage) {
        this.responseCode = responseCode;
        this.detailMessage = detailMessage == null ? responseCode.getMsg() : detailMessage;
    }

    public Integer getCode() {
        return responseCode.getCode();
    }

}