package com.leetmodel.common.messaging;

import java.util.Map;

/** 业务服务可选提供的领域任务积压统计，键必须是固定低基数字符串。 */
@FunctionalInterface
public interface MessagingDomainBacklogContributor {

    Map<String, Long> backlog();
}
