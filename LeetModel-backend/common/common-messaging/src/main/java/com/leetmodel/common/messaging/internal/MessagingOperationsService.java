package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOverviewDTO;
import com.leetmodel.common.api.dto.MessagingReplayRequestDTO;
import com.leetmodel.common.messaging.MessagingDomainBacklogContributor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 汇总本服务可靠消息状态，并执行受限且可审计的人工命令。 */
@Slf4j
public final class MessagingOperationsService {

    public static final String REPLAY_MODE = "MANUAL_OUTBOX_EVENT_ID_ONLY";

    private final String service;
    private final JdbcMessageOutbox outbox;
    private final JdbcMessageInbox inbox;
    private final RocketMqConsumerControl consumerControl;
    private final MessagingMetrics metrics;
    private final RocketMqDeadLetterOperations deadLetters;
    private final List<MessagingDomainBacklogContributor> backlogContributors;
    private final OperationAuditGovernanceProducer audit;

    /**
     * 构造消息运维应用服务（不含审计门禁）。
     *
     * @param service             当前微服务标识
     * @param outbox              本地 Outbox 仓库
     * @param inbox               本地 Inbox 仓库
     * @param consumerControl     RocketMQ 消费者控制器
     * @param metrics             消息指标
     * @param deadLetters         RocketMQ 死信队列查询器
     * @param backlogContributors 领域积压贡献者列表
     */
    public MessagingOperationsService(
            String service,
            JdbcMessageOutbox outbox,
            JdbcMessageInbox inbox,
            RocketMqConsumerControl consumerControl,
            MessagingMetrics metrics,
            RocketMqDeadLetterOperations deadLetters,
            List<MessagingDomainBacklogContributor> backlogContributors
    ) {
        this(service, outbox, inbox, consumerControl, metrics, deadLetters, backlogContributors, null);
    }

    /**
     * 构造消息运维应用服务（包含操作审计门禁）。
     *
     * @param service             当前微服务标识
     * @param outbox              本地 Outbox 仓库
     * @param inbox               本地 Inbox 仓库
     * @param consumerControl     RocketMQ 消费者控制器
     * @param metrics             消息指标
     * @param deadLetters         RocketMQ 死信队列查询器
     * @param backlogContributors 领域积压贡献者列表
     * @param audit               操作审计生产者，可为 null
     */
    public MessagingOperationsService(
            String service,
            JdbcMessageOutbox outbox,
            JdbcMessageInbox inbox,
            RocketMqConsumerControl consumerControl,
            MessagingMetrics metrics,
            RocketMqDeadLetterOperations deadLetters,
            List<MessagingDomainBacklogContributor> backlogContributors,
            OperationAuditGovernanceProducer audit
    ) {
        this.service = service;
        this.outbox = outbox;
        this.inbox = inbox;
        this.consumerControl = consumerControl;
        this.metrics = metrics;
        this.deadLetters = deadLetters;
        this.backlogContributors = List.copyOf(backlogContributors);
        this.audit = audit;
    }

    /**
     * 汇总当前服务消息链路全景运维大盘（Outbox 状态分布、Inbox 已消费量、消费者运行态与领域积压）。
     *
     * @return 包含全景统计指标的概览 DTO
     */
    public MessagingOverviewDTO overview() {
        Map<String, Long> outboxCounts = new LinkedHashMap<>();
        for (OutboxStatus status : OutboxStatus.values()) {
            outboxCounts.put(status.name(), outbox.count(status));
        }
        Map<String, Long> domainBacklog = new LinkedHashMap<>();
        backlogContributors.forEach(value -> domainBacklog.putAll(value.backlog()));
        return new MessagingOverviewDTO(service, outboxCounts, inbox.consumedCount(),
                outbox.oldestPendingAgeSeconds(), consumerControl.statuses(), domainBacklog, REPLAY_MODE);
    }

    /**
     * 组合筛选当前微服务的本地 Outbox 运维记录。
     *
     * @param status  可选的状态筛选过滤条件
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   单次拉取数量上限
     * @return 符合筛选条件的 Outbox 记录列表
     */
    public List<MessagingOutboxRecordDTO> outbox(
            String status,
            String traceId,
            String eventId,
            int limit
    ) {
        return outbox.findOperations(service, status, traceId, eventId, limit);
    }

    /**
     * 组合筛选当前微服务的本地 Inbox 消费记录。
     *
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   单次拉取数量上限
     * @return 符合筛选条件的 Inbox 记录列表
     */
    public List<MessagingInboxRecordDTO> inbox(
            String traceId,
            String eventId,
            int limit
    ) {
        return inbox.findOperations(service, traceId, eventId, limit);
    }

    /**
     * 获取当前微服务所有消费组关联的死信队列摘要集合。
     *
     * @return 死信队列摘要 DTO 列表
     */
    public List<MessagingDeadLetterQueueDTO> deadLetters() {
        return deadLetters.summaries();
    }

    /**
     * 在死信队列中按事件 ID 检索定位死信明细。
     *
     * @param consumerGroup 目标消费组名称
     * @param eventIds      待检索的事件 ID 列表
     * @return 检索到的死信明细记录列表
     */
    public List<MessagingDeadLetterRecordDTO> locateDeadLetters(
            String consumerGroup,
            List<String> eventIds
    ) {
        return deadLetters.locate(consumerGroup, eventIds);
    }

    /**
     * 人工触发指定事件的 Outbox 消息重放。
     *
     * @param request 包含待重放事件 ID 列表与人工原因说明的请求对象
     * @return 操作执行结果明细
     * @throws IllegalArgumentException 若请求参数格式不满足安全约束
     */
    public MessagingOperationResultDTO replay(MessagingReplayRequestDTO request) {
        if (request == null || request.eventIds() == null || request.eventIds().isEmpty()
                || request.eventIds().size() > 20
                || request.reason() == null || request.reason().trim().length() < 3
                || request.reason().length() > 200) {
            throw new IllegalArgumentException("补发需提供 1-20 个 eventId 和 3-200 字原因");
        }
        if (audit != null) audit.assertReady("OUTBOX.REPLAY");
        List<String> accepted = outbox.replay(request.eventIds(), request.reason().trim());
        metrics.replayed(accepted.size());
        if (audit != null && !accepted.isEmpty()) audit.emit("OUTBOX.REPLAY", "MESSAGE_OUTBOX", service,
                Map.of("replayCount", String.valueOf(accepted.size()), "replayReasonCode", "ADMIN_REQUEST",
                        "eventHash", "REDACTED"));
        log.warn("消息人工补发 service={}, requested={}, accepted={}",
                service, request.eventIds().size(), accepted.size());
        return new MessagingOperationResultDTO(service, "OUTBOX_REPLAY", accepted.size(), accepted);
    }

    /**
     * 人工暂停指定消费组的消息消费。
     *
     * @param consumerGroup 待暂停的目标消费组名称
     * @return 操作执行结果
     */
    public MessagingOperationResultDTO pause(String consumerGroup) {
        if (audit != null) audit.assertReady("CONSUMER.PAUSE");
        boolean changed = consumerControl.pause(consumerGroup);
        if (changed) metrics.consumerPaused();
        if (changed && audit != null) audit.emit("CONSUMER.PAUSE", "MESSAGE_CONSUMER", stableTarget(consumerGroup),
                Map.of("consumerGroup", consumerGroup, "pauseReasonCode", "ADMIN_REQUEST"));
        log.warn("消息消费人工暂停 service={}, consumerGroup={}, changed={}", service, consumerGroup, changed);
        return new MessagingOperationResultDTO(service, "CONSUMER_PAUSE", changed ? 1 : 0,
                changed ? List.of(consumerGroup) : List.of());
    }

    /**
     * 人工恢复指定消费组的消息消费。
     *
     * @param consumerGroup 待恢复的目标消费组名称
     * @return 操作执行结果
     */
    public MessagingOperationResultDTO resume(String consumerGroup) {
        if (audit != null) audit.assertReady("CONSUMER.RESUME");
        boolean changed = consumerControl.resume(consumerGroup);
        if (changed) metrics.consumerResumed();
        if (changed && audit != null) audit.emit("CONSUMER.RESUME", "MESSAGE_CONSUMER", stableTarget(consumerGroup),
                Map.of("consumerGroup", consumerGroup, "resumeReasonCode", "ADMIN_REQUEST"));
        log.warn("消息消费人工恢复 service={}, consumerGroup={}, changed={}", service, consumerGroup, changed);
        return new MessagingOperationResultDTO(service, "CONSUMER_RESUME", changed ? 1 : 0,
                changed ? List.of(consumerGroup) : List.of());
    }

    /**
     * 规范化审计目标标识，替换特殊字符。
     *
     * @param value 原始消费组名称
     * @return 安全的审计目标字符串
     */
    private String stableTarget(String value) {
        return value == null ? "unknown-consumer" : value.replace('%', '-');
    }

}
