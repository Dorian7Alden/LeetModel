package com.leetmodel.audit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 审计保留治理配置；删除在线事实不属于本服务自动动作。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "leetmodel.audit.retention")
public class AuditRetentionProperties {
    @Min(1)
    private int onlineRetentionDays = 365;
    private boolean archiveEnabled = false;
    private boolean backupRequired = true;
    @NotBlank
    private String policyVersion = "v1";
}
