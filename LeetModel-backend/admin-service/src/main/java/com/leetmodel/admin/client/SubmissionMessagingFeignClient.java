package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "submission-service", contextId = "submissionMessagingFeignClient")
public interface SubmissionMessagingFeignClient extends MessagingOperationsFeignContract {
}
