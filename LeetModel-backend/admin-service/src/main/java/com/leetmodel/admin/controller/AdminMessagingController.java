package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.service.AdminMessagingOperationsService;
import com.leetmodel.common.api.dto.MessagingFleetOverviewDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterReplayRequestDTO;
import com.leetmodel.common.api.dto.MessagingInboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingOperationResultDTO;
import com.leetmodel.common.api.dto.MessagingOutboxRecordDTO;
import com.leetmodel.common.api.dto.MessagingReplayRequestDTO;
import com.leetmodel.common.api.dto.MessagingTraceDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 可靠消息统一运维入口；所有读写操作均要求管理员角色。 */
@Validated
@RestController
@RequestMapping("/api/admin/messaging")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminMessagingController {

    private final AdminMessagingOperationsService operations;

    @GetMapping("/overview")
    public Result<MessagingFleetOverviewDTO> overview() {
        return Result.ok(operations.overview());
    }

    @GetMapping("/services/{service}/outbox")
    public Result<List<MessagingOutboxRecordDTO>> outbox(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        return Result.ok(operations.outbox(service, status, traceId, eventId, limit));
    }

    @GetMapping("/services/{service}/inbox")
    public Result<List<MessagingInboxRecordDTO>> inbox(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit) {
        return Result.ok(operations.inbox(service, traceId, eventId, limit));
    }

    @GetMapping("/traces/{traceId}")
    public Result<MessagingTraceDTO> trace(
            @PathVariable @Pattern(regexp = "[a-zA-Z0-9:._-]{1,100}") String traceId) {
        return Result.ok(operations.trace(traceId));
    }

    @PostMapping("/services/{service}/outbox/replay")
    public Result<MessagingOperationResultDTO> replay(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @RequestBody @Valid MessagingReplayRequestDTO request) {
        return Result.ok(operations.replay(service, request, UserContext.getUserId()));
    }

    @PostMapping("/services/{service}/consumers/{consumerGroup}/pause")
    public Result<MessagingOperationResultDTO> pause(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @PathVariable @Size(max = 255) String consumerGroup) {
        return Result.ok(operations.pause(service, consumerGroup));
    }

    @PostMapping("/services/{service}/consumers/{consumerGroup}/resume")
    public Result<MessagingOperationResultDTO> resume(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @PathVariable @Size(max = 255) String consumerGroup) {
        return Result.ok(operations.resume(service, consumerGroup));
    }

    @GetMapping("/services/{service}/dlq")
    public Result<List<MessagingDeadLetterQueueDTO>> deadLetters(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service) {
        return Result.ok(operations.deadLetters(service));
    }

    @PostMapping("/services/{service}/dlq/replay")
    public Result<MessagingOperationResultDTO> replayDeadLetters(
            @PathVariable @Pattern(regexp = "[a-z-]{3,40}") String service,
            @RequestBody @Valid MessagingDeadLetterReplayRequestDTO request) {
        return Result.ok(operations.replayDeadLetters(
                service, request, UserContext.getUserId()));
    }
}
