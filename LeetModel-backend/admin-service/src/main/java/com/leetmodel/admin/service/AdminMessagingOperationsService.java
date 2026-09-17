package com.leetmodel.admin.service;

import com.leetmodel.admin.client.EvaluationMessagingFeignClient;
import com.leetmodel.admin.client.RankingMessagingFeignClient;
import com.leetmodel.admin.client.ReviewMessagingFeignClient;
import com.leetmodel.admin.client.SubmissionMessagingFeignClient;
import com.leetmodel.admin.client.SuggestionMessagingFeignClient;
import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.MessagingFleetOverviewDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterLocateRequestDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterReplayRequestDTO;
import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOverviewDTO;
import com.leetmodel.common.api.dto.MessagingReplayRequestDTO;
import com.leetmodel.common.api.dto.MessagingTraceDTO;
import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.api.feign.MessagingOperationsFeignContract;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 跨五个本地数据库聚合可靠消息运维事实，不持有任何业务数据库连接。 */
@Slf4j
@Service
public class AdminMessagingOperationsService {

    private final Map<String, MessagingOperationsFeignContract> clients;
    private final AiGatewayFeignClient aiGateway;

    public AdminMessagingOperationsService(
            SubmissionMessagingFeignClient submission,
            ReviewMessagingFeignClient review,
            RankingMessagingFeignClient ranking,
            SuggestionMessagingFeignClient suggestion,
            EvaluationMessagingFeignClient evaluation,
            AiGatewayFeignClient aiGateway
    ) {
        this.clients = new LinkedHashMap<>();
        clients.put("submission-service", submission);
        clients.put("ai-review-service", review);
        clients.put("ranking-service", ranking);
        clients.put("ai-suggestion-service", suggestion);
        clients.put("ai-evaluation-service", evaluation);
        this.aiGateway = aiGateway;
    }

    public MessagingFleetOverviewDTO overview() {
        List<MessagingOverviewDTO> available = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        clients.forEach((service, client) -> collectOverview(service, unavailable,
                () -> client.messagingOverview(), available));
        return new MessagingFleetOverviewDTO(available, unavailable);
    }

    public List<MessagingOutboxRecordDTO> outbox(
            String service, String status, String traceId, String eventId, int limit) {
        return requiredData(client(service).messagingOutbox(status, traceId, eventId, limit), service);
    }

    public List<MessagingInboxRecordDTO> inbox(
            String service, String traceId, String eventId, int limit) {
        return requiredData(client(service).messagingInbox(traceId, eventId, limit), service);
    }

    public MessagingOperationResultDTO replay(
            String service, MessagingReplayRequestDTO request, Long operatorId) {
        String reason = request.reason().trim();
        reason = reason.substring(0, Math.min(reason.length(), 160));
        MessagingReplayRequestDTO audited = new MessagingReplayRequestDTO(
                request.eventIds(), reason + " [operator=" + operatorId + "]");
        return requiredData(client(service).replayMessagingOutbox(audited), service);
    }

    public MessagingOperationResultDTO pause(String service, String consumerGroup) {
        return requiredData(client(service).pauseMessagingConsumer(consumerGroup), service);
    }

    public MessagingOperationResultDTO resume(String service, String consumerGroup) {
        return requiredData(client(service).resumeMessagingConsumer(consumerGroup), service);
    }

    public List<MessagingDeadLetterQueueDTO> deadLetters(String service) {
        return requiredData(client(service).messagingDeadLetters(), service);
    }

    /**
     * 先在消费服务 Broker DLQ 中逐项定位，再按信封 sourceService 从原始 Outbox 恢复。
     * 任一 eventId 未定位时整批拒绝，不允许把普通已发布事件伪装成死信重放。
     */
    public MessagingOperationResultDTO replayDeadLetters(
            String consumerService, MessagingDeadLetterReplayRequestDTO request, Long operatorId) {
        List<String> requestedIds = request.eventIds().stream().distinct().toList();
        List<MessagingDeadLetterRecordDTO> located = requiredData(
                client(consumerService).locateMessagingDeadLetters(
                        new MessagingDeadLetterLocateRequestDTO(request.consumerGroup(), requestedIds)),
                consumerService);
        if (located.size() != requestedIds.size()
                || !located.stream().map(MessagingDeadLetterRecordDTO::eventId)
                .collect(Collectors.toSet()).containsAll(requestedIds)) {
            throw new IllegalArgumentException("部分 eventId 未在指定 DLQ 中定位，整批未执行");
        }
        String reason = request.reason().trim();
        reason = reason.substring(0, Math.min(reason.length(), 120));
        String auditedReason = "DLQ " + request.consumerGroup() + ": " + reason
                + " [operator=" + operatorId + "]";
        List<String> accepted = new ArrayList<>();
        located.stream().collect(Collectors.groupingBy(
                MessagingDeadLetterRecordDTO::sourceService, LinkedHashMap::new, Collectors.toList()))
                .forEach((sourceService, records) -> {
                    MessagingOperationResultDTO result = requiredData(client(sourceService)
                            .replayMessagingOutbox(new MessagingReplayRequestDTO(
                                    records.stream().map(MessagingDeadLetterRecordDTO::eventId).toList(),
                                    auditedReason)), sourceService);
                    accepted.addAll(result.acceptedIds());
                });
        return new MessagingOperationResultDTO(consumerService, "DLQ_REPLAY", accepted.size(), accepted);
    }

    public MessagingTraceDTO trace(String traceId) {
        List<MessagingOutboxRecordDTO> outbox = new ArrayList<>();
        List<MessagingInboxRecordDTO> inbox = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        clients.forEach((service, client) -> {
            collectList(service, unavailable,
                    () -> client.messagingOutbox(null, traceId, null, 100), outbox);
            collectList(service, unavailable,
                    () -> client.messagingInbox(traceId, null, 100), inbox);
        });
        AiCallQueryDTO query = new AiCallQueryDTO();
        query.setTraceId(traceId);
        query.setLimit(100);
        List<AiCallLogDTO> aiCalls = new ArrayList<>();
        collectList("ai-gateway-service", unavailable, () -> aiGateway.listCalls(query), aiCalls);
        return new MessagingTraceDTO(traceId, outbox, inbox, aiCalls,
                unavailable.stream().distinct().toList());
    }

    private MessagingOperationsFeignContract client(String service) {
        MessagingOperationsFeignContract client = clients.get(service);
        if (client == null) {
            throw new IllegalArgumentException("服务不在可靠消息运维范围");
        }
        return client;
    }

    private <T> void collectList(String service, List<String> unavailable,
                             RemoteResult<List<T>> remote, List<T> target) {
        try {
            List<T> data = requiredData(remote.get(), service);
            if (data != null) target.addAll(data);
        } catch (RuntimeException exception) {
            unavailable.add(service);
            log.warn("消息运维聚合失败 service={}, type={}", service,
                    exception.getClass().getSimpleName());
        }
    }

    private void collectOverview(String service, List<String> unavailable,
                         RemoteResult<MessagingOverviewDTO> remote,
                         List<MessagingOverviewDTO> target) {
        try {
            target.add(requiredData(remote.get(), service));
        } catch (RuntimeException exception) {
            unavailable.add(service);
            log.warn("消息运维概览失败 service={}, type={}", service,
                    exception.getClass().getSimpleName());
        }
    }

    private <T> T requiredData(Result<T> result, String service) {
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException(service + " 运维端点不可用");
        }
        return result.getData();
    }

    @FunctionalInterface
    private interface RemoteResult<T> {
        Result<T> get();
    }
}
