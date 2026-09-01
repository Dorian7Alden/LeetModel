package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterLocateRequestDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOverviewDTO;
import com.leetmodel.common.api.dto.MessagingReplayRequestDTO;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 仅供内网管理聚合调用的可靠消息运维端点。 */
@Validated
@RestController
@RequestMapping("/internal/messaging")
public class MessagingOperationsController {

    private final MessagingOperationsService operations;

    public MessagingOperationsController(MessagingOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/overview")
    public Result<MessagingOverviewDTO> overview() {
        return Result.ok(operations.overview());
    }

    @GetMapping("/outbox")
    public Result<List<MessagingOutboxRecordDTO>> outbox(
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        return Result.ok(operations.outbox(status, traceId, eventId, limit));
    }

    @GetMapping("/inbox")
    public Result<List<MessagingInboxRecordDTO>> inbox(
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        return Result.ok(operations.inbox(traceId, eventId, limit));
    }

    @GetMapping("/dlq")
    public Result<List<MessagingDeadLetterQueueDTO>> deadLetters() {
        return Result.ok(operations.deadLetters());
    }

    @PostMapping("/dlq/locate")
    public Result<List<MessagingDeadLetterRecordDTO>> locateDeadLetters(
            @RequestBody @Valid MessagingDeadLetterLocateRequestDTO request) {
        return Result.ok(operations.locateDeadLetters(request.consumerGroup(), request.eventIds()));
    }

    @PostMapping("/outbox/replay")
    public Result<MessagingOperationResultDTO> replay(@RequestBody @Valid MessagingReplayRequestDTO request) {
        return Result.ok(operations.replay(request));
    }

    @PostMapping("/consumers/{consumerGroup}/pause")
    public Result<MessagingOperationResultDTO> pause(
            @PathVariable @Size(max = 255) String consumerGroup) {
        return Result.ok(operations.pause(consumerGroup));
    }

    @PostMapping("/consumers/{consumerGroup}/resume")
    public Result<MessagingOperationResultDTO> resume(
            @PathVariable @Size(max = 255) String consumerGroup) {
        return Result.ok(operations.resume(consumerGroup));
    }
}
