package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterLocateRequestDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOverviewDTO;
import com.leetmodel.common.api.dto.MessagingReplayRequestDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** 可靠消息本地运维端点契约，由管理服务为每个消息服务绑定独立 Feign 客户端。 */
public interface MessagingOperationsFeignContract {

    @GetMapping("/internal/messaging/overview")
    Result<MessagingOverviewDTO> messagingOverview();

    @GetMapping("/internal/messaging/outbox")
    Result<List<MessagingOutboxRecordDTO>> messagingOutbox(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "eventId", required = false) String eventId,
            @RequestParam("limit") Integer limit);

    @GetMapping("/internal/messaging/inbox")
    Result<List<MessagingInboxRecordDTO>> messagingInbox(
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "eventId", required = false) String eventId,
            @RequestParam("limit") Integer limit);

    @GetMapping("/internal/messaging/dlq")
    Result<List<MessagingDeadLetterQueueDTO>> messagingDeadLetters();

    @PostMapping("/internal/messaging/dlq/locate")
    Result<List<MessagingDeadLetterRecordDTO>> locateMessagingDeadLetters(
            @RequestBody MessagingDeadLetterLocateRequestDTO request);

    @PostMapping("/internal/messaging/outbox/replay")
    Result<MessagingOperationResultDTO> replayMessagingOutbox(@RequestBody MessagingReplayRequestDTO request);

    @PostMapping("/internal/messaging/consumers/{consumerGroup}/pause")
    Result<MessagingOperationResultDTO> pauseMessagingConsumer(
            @PathVariable("consumerGroup") String consumerGroup);

    @PostMapping("/internal/messaging/consumers/{consumerGroup}/resume")
    Result<MessagingOperationResultDTO> resumeMessagingConsumer(
            @PathVariable("consumerGroup") String consumerGroup);
}
