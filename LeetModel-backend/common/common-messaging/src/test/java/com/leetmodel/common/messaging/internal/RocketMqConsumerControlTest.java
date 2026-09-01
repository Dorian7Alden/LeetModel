package com.leetmodel.common.messaging.internal;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

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
}
