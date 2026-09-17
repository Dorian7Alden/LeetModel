package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "ai-evaluation-service", contextId = "evaluationMessagingFeignClient")
public interface EvaluationMessagingFeignClient extends MessagingOperationsFeignContract {
}
