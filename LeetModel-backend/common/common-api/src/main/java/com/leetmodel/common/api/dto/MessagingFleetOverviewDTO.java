package com.leetmodel.common.api.dto;

import java.util.List;

/** 管理端聚合的消息服务集群视图，允许部分服务不可用。 */
public record MessagingFleetOverviewDTO(
        List<MessagingOverviewDTO> services,
        List<String> unavailableServices
) {
}
