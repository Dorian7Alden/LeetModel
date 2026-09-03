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

    public List<MessagingOutboxRecordDTO> outbox(
            String status, String traceId, String eventId, int limit) {
        return outbox.findOperations(service, status, traceId, eventId, limit);
    }

    public List<MessagingInboxRecordDTO> inbox(String traceId, String eventId, int limit) {
        return inbox.findOperations(service, traceId, eventId, limit);
    }

    public List<MessagingDeadLetterQueueDTO> deadLetters() {
        return deadLetters.summaries();
    }

    public List<MessagingDeadLetterRecordDTO> locateDeadLetters(
            String consumerGroup, List<String> eventIds) {
        return deadLetters.locate(consumerGroup, eventIds);
    }

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

    private String stableTarget(String value) {
        return value == null ? "unknown-consumer" : value.replace('%', '-');
    }

}
