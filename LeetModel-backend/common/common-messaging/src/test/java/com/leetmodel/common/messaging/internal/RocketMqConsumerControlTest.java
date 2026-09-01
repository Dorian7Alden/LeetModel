package com.leetmodel.common.messaging.internal;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.store.OffsetStore;
import org.apache.rocketmq.client.consumer.store.ReadOffsetType;
import org.apache.rocketmq.client.impl.consumer.DefaultMQPushConsumerImpl;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.QueueTimeSpan;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqConsumerControlTest {

    @Test
    void shouldPauseAndResumeOnlyKnownRunningConsumer() {
        ApplicationContext context = mock(ApplicationContext.class);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("lm-test%cg-review");
        when(container.getConsumerGroup()).thenReturn("lm-test%cg-review");
        when(container.getTopic()).thenReturn("lm-test%review-task-v1");
        when(container.getConsumer()).thenReturn(consumer);
        when(container.isRunning()).thenReturn(true);
        when(context.getBeansOfType(DefaultRocketMQListenerContainer.class, false, false))
                .thenReturn(Map.of("container", container));
        RocketMqConsumerControl control = new RocketMqConsumerControl(context);

        assertThat(control.pause("lm-test%cg-review")).isTrue();
        assertThat(consumer.isPause()).isTrue();
        assertThat(control.statuses()).singleElement()
                .satisfies(status -> assertThat(status.paused()).isTrue());
        assertThat(control.resume("lm-test%cg-review")).isTrue();
        assertThat(consumer.isPause()).isFalse();
        assertThatThrownBy(() -> control.pause("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReportBacklogAndOldestUnconsumedAge() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumerImpl consumerImpl = mock(DefaultMQPushConsumerImpl.class);
        OffsetStore offsetStore = mock(OffsetStore.class);
        MessageQueue firstQueue = new MessageQueue("lm-test%review-task-v1", "broker-a", 0);
        MessageQueue secondQueue = new MessageQueue("lm-test%review-task-v1", "broker-a", 1);
        QueueTimeSpan firstSpan = queueTimeSpan(125_000L);
        QueueTimeSpan secondSpan = queueTimeSpan(80_000L);

        when(container.getConsumerGroup()).thenReturn("lm-test%cg-review");
        when(container.getTopic()).thenReturn("lm-test%review-task-v1");
        when(container.getConsumer()).thenReturn(consumer);
        when(container.isRunning()).thenReturn(true);
        when(consumer.fetchSubscribeMessageQueues("lm-test%review-task-v1"))
                .thenReturn(Set.of(firstQueue, secondQueue));
        when(consumer.maxOffset(firstQueue)).thenReturn(10L);
        when(consumer.maxOffset(secondQueue)).thenReturn(8L);
        when(consumer.getOffsetStore()).thenReturn(offsetStore);
        when(offsetStore.readOffset(firstQueue, ReadOffsetType.MEMORY_FIRST_THEN_STORE))
                .thenReturn(6L);
        when(offsetStore.readOffset(secondQueue, ReadOffsetType.MEMORY_FIRST_THEN_STORE))
                .thenReturn(5L);
        when(consumer.getDefaultMQPushConsumerImpl()).thenReturn(consumerImpl);
        when(consumerImpl.queryConsumeTimeSpan("lm-test%review-task-v1"))
                .thenReturn(List.of(firstSpan, secondSpan));
        when(context.getBeansOfType(DefaultRocketMQListenerContainer.class, false, false))
                .thenReturn(Map.of("container", container));

        assertThat(new RocketMqConsumerControl(context).backlogs()).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.backlog()).isEqualTo(7L);
                    assertThat(snapshot.oldestUnconsumedSeconds()).isEqualTo(125L);
                    assertThat(snapshot.available()).isTrue();
                });
    }

    @Test
    void shouldMarkMetricsUnavailableWhenOldestAgeQueryFails() throws Exception {
        ApplicationContext context = mock(ApplicationContext.class);
        DefaultRocketMQListenerContainer container = mock(DefaultRocketMQListenerContainer.class);
        DefaultMQPushConsumer consumer = mock(DefaultMQPushConsumer.class);
        DefaultMQPushConsumerImpl consumerImpl = mock(DefaultMQPushConsumerImpl.class);
        OffsetStore offsetStore = mock(OffsetStore.class);
        MessageQueue queue = new MessageQueue("lm-test%review-task-v1", "broker-a", 0);

        when(container.getConsumerGroup()).thenReturn("lm-test%cg-review");
        when(container.getTopic()).thenReturn("lm-test%review-task-v1");
        when(container.getConsumer()).thenReturn(consumer);
        when(container.isRunning()).thenReturn(true);
        when(consumer.fetchSubscribeMessageQueues("lm-test%review-task-v1"))
                .thenReturn(Set.of(queue));
        when(consumer.maxOffset(queue)).thenReturn(10L);
        when(consumer.getOffsetStore()).thenReturn(offsetStore);
        when(offsetStore.readOffset(queue, ReadOffsetType.MEMORY_FIRST_THEN_STORE))
                .thenReturn(6L);
        when(consumer.getDefaultMQPushConsumerImpl()).thenReturn(consumerImpl);
        when(consumerImpl.queryConsumeTimeSpan("lm-test%review-task-v1"))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(context.getBeansOfType(DefaultRocketMQListenerContainer.class, false, false))
                .thenReturn(Map.of("container", container));

        assertThat(new RocketMqConsumerControl(context).backlogs()).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.backlog()).isZero();
                    assertThat(snapshot.oldestUnconsumedSeconds()).isZero();
                    assertThat(snapshot.available()).isFalse();
                });
    }

    private static QueueTimeSpan queueTimeSpan(long delayMillis) {
        QueueTimeSpan span = new QueueTimeSpan();
        span.setDelayTime(delayMillis);
        return span;
    }
}
