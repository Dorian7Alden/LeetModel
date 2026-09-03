package com.leetmodel.audit.service;

import com.leetmodel.audit.config.AuditRetentionProperties;
import com.leetmodel.common.api.dto.OperationAuditRetentionPolicyDTO;
import org.springframework.stereotype.Service;

/** 只读返回当前环境保留策略；实际归档/备份由部署平台执行。 */
@Service
public class AuditRetentionPolicyService {
    private final AuditRetentionProperties properties;

    public AuditRetentionPolicyService(AuditRetentionProperties properties) {
        this.properties = properties;
    }

    public OperationAuditRetentionPolicyDTO current() {
        return new OperationAuditRetentionPolicyDTO(properties.getOnlineRetentionDays(),
                properties.isArchiveEnabled(), properties.isBackupRequired(), properties.getPolicyVersion());
    }
}
