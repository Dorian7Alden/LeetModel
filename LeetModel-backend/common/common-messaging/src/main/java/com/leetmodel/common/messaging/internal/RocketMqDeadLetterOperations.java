package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingDeadLetterQueueDTO;
import com.leetmodel.common.api.dto.MessagingDeadLetterRecordDTO;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeV1;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 使用当前已启动 Producer 的只读管理 API 定位 Broker DLQ，不创建自动回灌消费者。 */
@Slf4j
public final class RocketMqDeadLetterOperations {

    private final String service;
    private final RocketMQTemplate template;
    private final MessageCodec codec;
    private final RocketMqConsumerControl consumerControl;

    /**
     * 构造 RocketMQ 死信队列运维操作工具。
     *
     * @param service         当前微服务名称
     * @param template        RocketMQ 模板客户端，可为 null
     * @param codec           消息编解码器
     * @param consumerControl 消费者控制组件
     */
    public RocketMqDeadLetterOperations(
            String service,
            RocketMQTemplate template,
            MessageCodec codec,
            RocketMqConsumerControl consumerControl
    ) {
        this.service = service;
        this.template = template;
        this.codec = codec;
        this.consumerControl = consumerControl;
    }

    /**
     * 汇总当前微服务全部消费组对应 DLQ 死信队列的元数据摘要。
     *
     * @return 死信队列摘要列表
     */
    public List<MessagingDeadLetterQueueDTO> summaries() {
        return consumerControl.statuses().stream().map(consumer -> summary(consumer.consumerGroup())).toList();
    }

    /**
     * 在特定消费组关联的 DLQ 中按事件 ID 检索定位死信消息。
     *
     * @param consumerGroup 目标消费组名称
     * @param eventIds      待匹配的事件 ID 列表
     * @return 匹配到的死信明细列表
     */
    public List<MessagingDeadLetterRecordDTO> locate(
            String consumerGroup,
            List<String> eventIds
    ) {
        requireLocalConsumer(consumerGroup);
        if (template == null) throw new IllegalStateException("RocketMQ 管理连接不可用");
        DefaultMQProducer producer = template.getProducer();
        String topic = MixAll.DLQ_GROUP_TOPIC_PREFIX + consumerGroup;
        long end = System.currentTimeMillis();
        long begin = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli();
        List<MessagingDeadLetterRecordDTO> records = new ArrayList<>();
        for (String eventId : eventIds.stream().distinct().limit(20).toList()) {
            if (eventId == null || !eventId.matches("[0-9a-fA-F-]{36}")) continue;
            try {
                QueryResult query = producer.queryMessage(topic, eventId, 8, begin, end);
                query.getMessageList().stream()
                        .filter(message -> hasKey(message, eventId))
                        .findFirst()
                        .map(message -> toRecord(consumerGroup, eventId, message))
                        .ifPresent(records::add);
            } catch (Exception exception) {
                if (isMissingTopic(exception)) continue;
                throw new IllegalStateException("DLQ 查询暂不可用", exception);
            }
        }
        return records;
    }

    /**
     * 采集指定消费组死信队列的消息积压数与最老消息时间。
     *
     * @param consumerGroup 消费组名称
     * @return 死信队列摘要 DTO
     */
    private MessagingDeadLetterQueueDTO summary(String consumerGroup) {
        String topic = MixAll.DLQ_GROUP_TOPIC_PREFIX + consumerGroup;
        if (template == null) {
            return new MessagingDeadLetterQueueDTO(service, consumerGroup, topic, 0L, null, false);
        }
        try {
            DefaultMQProducer producer = template.getProducer();
            List<MessageQueue> queues = producer.fetchPublishMessageQueues(topic);
            long count = 0L;
            long oldest = Long.MAX_VALUE;
            for (MessageQueue queue : queues) {
                long min = producer.minOffset(queue);
                long max = producer.maxOffset(queue);
                count += Math.max(0L, max - min);
                if (max > min) oldest = Math.min(oldest, producer.earliestMsgStoreTime(queue));
            }
            return new MessagingDeadLetterQueueDTO(service, consumerGroup, topic, count,
                    oldest == Long.MAX_VALUE ? null : localDateTime(oldest), true);
        } catch (Exception exception) {
            if (isMissingTopic(exception)) {
                return new MessagingDeadLetterQueueDTO(service, consumerGroup, topic, 0L, null, true);
            }
            log.debug("DLQ 摘要不可用 service={}, consumerGroup={}, type={}",
                    service, consumerGroup, exception.getClass().getSimpleName());
            return new MessagingDeadLetterQueueDTO(service, consumerGroup, topic, 0L, null, false);
        }
    }

    /**
     * 将 RocketMQ 原生死信消息实体反序列化并转换为运维明细 DTO。
     *
     * @param consumerGroup 消费组名称
     * @param eventId       事件唯一 ID
     * @param message       RocketMQ 原始消息扩展对象
     * @return 转换后的死信明细 DTO
     */
    private MessagingDeadLetterRecordDTO toRecord(
            String consumerGroup,
            String eventId,
            MessageExt message
    ) {
        MessageEnvelopeV1<Object> envelope = codec.decode(message.getBody(), Object.class);
        return new MessagingDeadLetterRecordDTO(service, consumerGroup, eventId,
                envelope.eventType(), envelope.sourceService(), message.getMsgId(),
                message.getReconsumeTimes(), localDateTime(message.getStoreTimestamp()));
    }

    /**
     * 校验消费组是否归属于当前微服务，防止跨服务误操作。
     *
     * @param consumerGroup 消费组名称
     * @throws IllegalArgumentException 若不属于当前服务
     */
    private void requireLocalConsumer(String consumerGroup) {
        Set<String> localGroups = consumerControl.statuses().stream()
                .map(value -> value.consumerGroup()).collect(java.util.stream.Collectors.toSet());
        if (!localGroups.contains(consumerGroup)) {
            throw new IllegalArgumentException("consumerGroup 不属于当前服务");
        }
    }

    /**
     * 检查 RocketMQ 消息的 keys 字段是否包含指定的事件 ID。
     *
     * @param message 待检查的消息
     * @param eventId 目标事件 ID
     * @return 若匹配返回 true，否则 false
     */
    private boolean hasKey(MessageExt message, String eventId) {
        String keys = message.getKeys();
        return keys != null && List.of(keys.split("\\s+")).contains(eventId);
    }

    /**
     * 将毫秒时间戳转换为 UTC LocalDateTime。
     *
     * @param epochMillis 毫秒时间戳
     * @return 转换后的 LocalDateTime 实例
     */
    private LocalDateTime localDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    /**
     * 判断异常是否因 Broker 尚未生成对应的 DLQ Topic 导致。
     *
     * @param error 异常对象
     * @return 若为 Topic 不存在导致的常规错误返回 true，否则 false
     */
    private boolean isMissingTopic(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("No route info")
                    || message.contains("TOPIC_NOT_EXIST")
                    || message.contains("topic route info not found"))) return true;
            current = current.getCause();
        }
        return false;
    }
}
