package com.leetmodel.common.api.dto;

/** 审计保留/归档/备份策略的只读投影。 */
public record OperationAuditRetentionPolicyDTO(
        int onlineRetentionDays,
        boolean archiveEnabled,
        boolean backupRequired,
        String policyVersion
) { }
