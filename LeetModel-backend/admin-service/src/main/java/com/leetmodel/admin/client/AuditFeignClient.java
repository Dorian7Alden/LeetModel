package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.OperationAuditFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "audit-service", contextId = "auditFeignClient", configuration = AuditFeignConfig.class)
public interface AuditFeignClient extends OperationAuditFeignContract { }
