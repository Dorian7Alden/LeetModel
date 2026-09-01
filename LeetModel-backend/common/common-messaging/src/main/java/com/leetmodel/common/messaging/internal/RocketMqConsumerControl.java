package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingConsumerDTO;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.List;

/** 对当前服务内已启动的 RocketMQ Push Consumer 执行真实挂起与恢复。 */
public final class RocketMqConsumerControl {

    private final ApplicationContext applicationContext;

    public RocketMqConsumerControl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<MessagingConsumerDTO> statuses() {
        return containers().stream()
                .map(value -> new MessagingConsumerDTO(
                        value.getConsumerGroup(), value.getTopic(),
                        value.getConsumer() != null && value.getConsumer().isPause(),
                        value.isRunning()))
                .sorted(Comparator.comparing(MessagingConsumerDTO::consumerGroup))
                .toList();
    }

    public boolean pause(String consumerGroup) {
        DefaultRocketMQListenerContainer container = required(consumerGroup);
        if (container.getConsumer() == null || !container.isRunning()) {
            return false;
        }
        container.getConsumer().suspend();
        return true;
    }

    public boolean resume(String consumerGroup) {
        DefaultRocketMQListenerContainer container = required(consumerGroup);
        if (container.getConsumer() == null || !container.isRunning()) {
            return false;
        }
        container.getConsumer().resume();
        return true;
    }

    private DefaultRocketMQListenerContainer required(String consumerGroup) {
        if (consumerGroup == null || consumerGroup.isBlank() || consumerGroup.length() > 255) {
            throw new IllegalArgumentException("consumerGroup 无效");
        }
        return containers().stream()
                .filter(value -> consumerGroup.equals(value.getConsumerGroup()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("consumerGroup 不属于当前服务"));
    }

    private List<DefaultRocketMQListenerContainer> containers() {
        return List.copyOf(applicationContext
                .getBeansOfType(DefaultRocketMQListenerContainer.class, false, false).values());
    }
}
