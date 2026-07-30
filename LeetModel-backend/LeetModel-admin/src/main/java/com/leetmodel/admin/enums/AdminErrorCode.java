package com.leetmodel.admin.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理后台模块错误码 —— BB=05。
 *
 * @author LeetModel
 */
@Getter
@AllArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    SERVICE_UNAVAILABLE(40501, "下游服务暂不可用"),
    ;

    private final int code;
    private final String message;
}
