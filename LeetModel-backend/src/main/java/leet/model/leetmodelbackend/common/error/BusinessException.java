package leet.model.leetmodelbackend.common.error;

import lombok.Getter;

/**
 * 业务异常，用于承载统一错误码和错误信息。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResponseCode responseCode;

    private final String detailMessage;

    public BusinessException(ResponseCode responseCode) {
        this(responseCode, responseCode.getMsg());
    }

    public BusinessException(ResponseCode responseCode, String detailMessage) {
        super(detailMessage == null ? responseCode.getMsg() : detailMessage);
        this.responseCode = responseCode;
        this.detailMessage = detailMessage == null ? responseCode.getMsg() : detailMessage;
    }

    public Integer getCode() {
        return responseCode.getCode();
    }

}