package com.leetmodel.submission.messaging;

import com.leetmodel.common.api.dto.ReviewTaskReadyPayload;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.PublishReceipt;

import java.util.Objects;

/**
 * Broker 长故障时读取同一 Outbox 的幂等 Feign 回退发布器。
 */
public final class FeignReviewTaskPublisher implements MessagePublisher {

    private final MessageCodec codec;
    private final ReviewFeignClient reviewFeignClient;

    /**
     * 创建 Feign Relay 发布器。
     *
     * @param codec 消息编解码器
     * @param reviewFeignClient 评审内部客户端
     */
    public FeignReviewTaskPublisher(MessageCodec codec, ReviewFeignClient reviewFeignClient) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.reviewFeignClient = Objects.requireNonNull(reviewFeignClient, "reviewFeignClient");
    }

    @Override
    public PublishReceipt publish(PendingMessage message) {
        MessageEnvelopeV1<ReviewTaskReadyPayload> envelope = codec.decode(
                codec.bytes(message.payloadJson()), ReviewTaskReadyPayload.class);
        if (!ReviewTaskMessageContract.EVENT_TYPE.equals(envelope.eventType())) {
            throw new MessageContractException("FEIGN_RELAY 不支持事件: " + envelope.eventType());
        }
        ReviewTaskReadyPayload payload = envelope.payload();
        Result<Long> result = reviewFeignClient.createVersionedTask(
                payload.submissionId(), payload.teamId(), payload.problemId(), payload.workflowVersion());
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("评审 Feign Relay 暂不可用");
        }
        return new PublishReceipt("feign:" + result.getData());
    }
}
