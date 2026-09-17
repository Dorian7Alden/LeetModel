package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "ai-review-service", contextId = "reviewMessagingFeignClient")
public interface ReviewMessagingFeignClient extends MessagingOperationsFeignContract {
}
