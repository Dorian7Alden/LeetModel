package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "ai-suggestion-service", contextId = "suggestionMessagingFeignClient")
public interface SuggestionMessagingFeignClient extends MessagingOperationsFeignContract {
}
