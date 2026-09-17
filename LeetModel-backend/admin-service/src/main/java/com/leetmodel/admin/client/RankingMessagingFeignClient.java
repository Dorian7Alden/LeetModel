package com.leetmodel.admin.client;

import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "ranking-service", contextId = "rankingMessagingFeignClient")
public interface RankingMessagingFeignClient extends MessagingOperationsFeignContract {
}
