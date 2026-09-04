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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息运维管理内部 Controller。
 *
 * <p>提供受权限保护的微服务内部端点，供 admin-service 聚合查询 Outbox/Inbox 状态、
 * 浏览死信队列、执行消息手动重放及暂停/恢复消费者。</p>
 */
@Validated
@RestController
@ConditionalOnBean(MessagingOperationsService.class)
@RequestMapping("/internal/messaging")
public class MessagingOperationsController {

    /** 消息运维应用服务 */
    private final MessagingOperationsService operations;

    /**
     * 构造消息运维 Controller。
     *
     * @param operations 消息运维应用服务实例
     */
    public MessagingOperationsController(MessagingOperationsService operations) {
        this.operations = operations;
    }

    /**
     * 查询当前微服务的消息全景总览（包含各状态积压量与消费组状态）。
     *
     * @return 包含 Outbox 各状态计数与 Inbox 统计的总览 DTO
     */
    @GetMapping("/overview")
    public Result<MessagingOverviewDTO> overview() {
        return Result.ok(operations.overview());
    }

    /**
     * 组合筛选当前微服务的本地 Outbox 发送记录。
     *
     * @param status  可选的状态筛选过滤条件（PENDING/PUBLISHED/BLOCKED）
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   单次拉取数量上限，默认 50，最大 100
     * @return 符合筛选条件的 Outbox 记录列表
     */
    @GetMapping("/outbox")
    public Result<List<MessagingOutboxRecordDTO>> outbox(
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit
    ) {
        return Result.ok(operations.outbox(status, traceId, eventId, limit));
    }

    /**
     * 组合筛选当前微服务的本地 Inbox 接收消费记录。
     *
     * @param traceId 可选的链路追踪 ID 筛选
     * @param eventId 可选的事件唯一 ID 筛选
     * @param limit   单次拉取数量上限，默认 50，最大 100
     * @return 符合筛选条件的 Inbox 记录列表
     */
    @GetMapping("/inbox")
    public Result<List<MessagingInboxRecordDTO>> inbox(
            @RequestParam(required = false) @Size(max = 100) String traceId,
            @RequestParam(required = false) @Size(max = 36) String eventId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) Integer limit
    ) {
        return Result.ok(operations.inbox(traceId, eventId, limit));
    }

    /**
     * 汇总当前服务关联的所有消费死信队列元数据。
     *
     * @return 死信队列摘要集合
     */
    @GetMapping("/dlq")
    public Result<List<MessagingDeadLetterQueueDTO>> deadLetters() {
        return Result.ok(operations.deadLetters());
    }

    /**
     * 精确检索死信队列中的指定消息记录。
     *
     * @param request 包含消费组与待定位 eventId 列表的请求参数对象
     * @return 检索到的死信明细记录列表
     */
    @PostMapping("/dlq/locate")
    public Result<List<MessagingDeadLetterRecordDTO>> locateDeadLetters(
            @RequestBody @Valid MessagingDeadLetterLocateRequestDTO request
    ) {
        return Result.ok(operations.locateDeadLetters(request.consumerGroup(), request.eventIds()));
    }

    /**
     * 人工审批重放阻断或积压的 Outbox 消息。
     *
     * @param request 包含待重放 eventId 列表与审计原因的重放请求对象
     * @return 操作执行结果明细
     */
    @PostMapping("/outbox/replay")
    public Result<MessagingOperationResultDTO> replay(
            @RequestBody @Valid MessagingReplayRequestDTO request
    ) {
        return Result.ok(operations.replay(request));
    }

    /**
     * 人工暂停指定消费组的消息消费。
     *
     * @param consumerGroup 待暂停的目标消费组名称
     * @return 操作执行结果
     */
    @PostMapping("/consumers/{consumerGroup}/pause")
    public Result<MessagingOperationResultDTO> pause(
            @PathVariable @Size(max = 255) String consumerGroup
    ) {
        return Result.ok(operations.pause(consumerGroup));
    }

    /**
     * 人工恢复指定消费组的消息消费。
     *
     * @param consumerGroup 待恢复的目标消费组名称
     * @return 操作执行结果
     */
    @PostMapping("/consumers/{consumerGroup}/resume")
    public Result<MessagingOperationResultDTO> resume(
            @PathVariable @Size(max = 255) String consumerGroup
    ) {
        return Result.ok(operations.resume(consumerGroup));
    }
}
