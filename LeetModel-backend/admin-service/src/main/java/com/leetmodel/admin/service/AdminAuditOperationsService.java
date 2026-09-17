package com.leetmodel.admin.service;

import com.leetmodel.admin.client.AuditFeignClient;
import com.leetmodel.common.api.dto.OperationAuditPageDTO;
import com.leetmodel.common.api.dto.OperationAuditQueryDTO;
import com.leetmodel.common.api.dto.OperationAuditRetentionPolicyDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 管理端审计代理；不持有 lm_audit 连接、不缓存审计副本。 */
@Service
@RequiredArgsConstructor
public class AdminAuditOperationsService {
    private final AuditFeignClient client;

    public OperationAuditPageDTO search(OperationAuditQueryDTO query) {
        return required(client.search(string(query.from()), string(query.to()), query.sourceService(),
                query.category(), query.operationCode(), query.riskLevel(), query.actorId(), query.targetType(),
                query.targetId(), query.outcome(), query.operationId(), query.traceId(), query.swTraceId(),
                query.limit()));
    }

    public OperationAuditRetentionPolicyDTO retentionPolicy() {
        return required(client.retentionPolicy());
    }

    private <T> T required(com.leetmodel.common.core.result.Result<T> result) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("audit-service 查询不可用");
        }
        return result.getData();
    }

    private String string(java.time.Instant value) { return value == null ? null : value.toString(); }
}
