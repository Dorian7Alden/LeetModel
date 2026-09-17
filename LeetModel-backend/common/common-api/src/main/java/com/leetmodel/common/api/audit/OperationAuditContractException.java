package com.leetmodel.common.api.audit;

/** 表示操作审计载荷不符合版本化公共契约。 */
public final class OperationAuditContractException extends RuntimeException {
    public OperationAuditContractException(String message) {
        super(message);
    }
}
