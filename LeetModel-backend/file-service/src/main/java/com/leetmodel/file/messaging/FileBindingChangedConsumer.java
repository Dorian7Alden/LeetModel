package com.leetmodel.file.messaging;

import com.leetmodel.common.api.dto.FileBindingChangedPayload;
import com.leetmodel.common.api.messaging.FileBindingMessageContract;
import com.leetmodel.common.core.telemetry.CorrelationContext;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageContractException;
import com.leetmodel.common.messaging.MessageCorrelationContext;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import com.leetmodel.common.messaging.MessageInbox;
import com.leetmodel.file.service.FileBindingService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费业务绑定与解绑事件，幂等维护文件引用投影。 */
@Component
@ConditionalOnProperty(prefix = "file.messaging", name = "consumer-enabled",
        havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${leetmodel.messaging.namespace:lm-dev}%file-event-v1",
        consumerGroup = "${leetmodel.messaging.namespace:lm-dev}%cg-file-binding-v1",
        selectorExpression = "*",
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING,
        consumeThreadNumber = 1,
        consumeThreadMax = 1,
        maxReconsumeTimes = 5
)
public class FileBindingChangedConsumer implements RocketMQListener<String> {

    private final MessageCodec codec;
    private final MessageInbox inbox;
    private final FileBindingService bindingService;

    public FileBindingChangedConsumer(
            MessageCodec codec,
            MessageInbox inbox,
            FileBindingService bindingService
    ) {
        this.codec = codec;
        this.inbox = inbox;
        this.bindingService = bindingService;
    }

    @Override
    public void onMessage(String body) {
        MessageEnvelopeV1<FileBindingChangedPayload> envelope = codec.decode(
                body, FileBindingChangedPayload.class);
        FileBindingChangedPayload payload = envelope.payload();
        validate(envelope, payload);
        try (CorrelationContext.Scope ignored = MessageCorrelationContext.open(envelope)) {
            inbox.executeOnce(FileBindingMessageContract.CONSUMER_GROUP, envelope, () -> {
                if (FileBindingMessageContract.BOUND_EVENT_TYPE.equals(envelope.eventType())) {
                    bindingService.bind(payload);
                } else {
                    bindingService.release(payload);
                }
            });
        }
    }

    private void validate(MessageEnvelopeV1<FileBindingChangedPayload> envelope,
                          FileBindingChangedPayload payload) {
        boolean bound = FileBindingMessageContract.BOUND_EVENT_TYPE.equals(envelope.eventType());
        boolean unbound = FileBindingMessageContract.UNBOUND_EVENT_TYPE.equals(envelope.eventType());
        if (!bound && !unbound) {
            throw new MessageContractException("文件绑定事件类型不受支持");
        }
        if (payload.fileId() == null || payload.fileId() <= 0
                || payload.eventVersion() == null || payload.eventVersion() <= 0) {
            throw new MessageContractException("文件绑定事件缺少文件标识或事件版本");
        }
        if (payload.ownerService() == null || !payload.ownerService().equals(envelope.sourceService())) {
            throw new MessageContractException("文件绑定事件所有者与来源服务不一致");
        }
        requireText(payload.ownerService(), "ownerService", 64);
        requireText(payload.resourceType(), "resourceType", 64);
        requireText(payload.resourceId(), "resourceId", 64);
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new MessageContractException(field + " 字段不合法");
        }
    }
}
