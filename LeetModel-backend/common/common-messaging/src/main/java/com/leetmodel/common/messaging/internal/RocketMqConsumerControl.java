package com.leetmodel.common.messaging.internal;

import com.leetmodel.common.api.dto.MessagingConsumerDTO;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.apache.rocketmq.client.consumer.store.ReadOffsetType;
import org.springframework.context.ApplicationContext;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/** 对当前服务内已启动的 RocketMQ Push Consumer 执行真实挂起与恢复。 */
public final class RocketMqConsumerControl {

    private final ApplicationContext applicationContext;

    /**
     * 构造 RocketMQ 消费者控制门面。
     *
     * @param applicationContext Spring 容器上下文，用于动态发现注册的消费者容器
     */
    public RocketMqConsumerControl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取当前服务内全部 RocketMQ 消费者的运行状态与暂停标记。
     *
     * @return 消费者状态信息列表
     */
    public List<MessagingConsumerDTO> statuses() {
        return containers().stream()
                .map(value -> new MessagingConsumerDTO(
                        value.getConsumerGroup(), value.getTopic(),
                        value.getConsumer() != null && value.getConsumer().isPause(),
                        value.isRunning()))
                .sorted(Comparator.comparing(MessagingConsumerDTO::consumerGroup))
                .toList();
    }

    /**
     * 暂停指定消费组的消息消费拉取。
     *
     * @param consumerGroup 待暂停的目标消费组名称
     * @return 若成功触发暂停返回 true；若消费者未运行或已失效返回 false
     */
    public boolean pause(String consumerGroup) {
        DefaultRocketMQListenerContainer container = required(consumerGroup);
        if (container.getConsumer() == null || !container.isRunning()) {
            return false;
        }
        container.getConsumer().suspend();
        return true;
    }

    /**
     * 恢复指定消费组的消息拉取与消费处理。
     *
     * @param consumerGroup 待恢复的目标消费组名称
     * @return 若成功触发恢复返回 true；若消费者未运行或已失效返回 false
     */
    public boolean resume(String consumerGroup) {
        DefaultRocketMQListenerContainer container = required(consumerGroup);
        if (container.getConsumer() == null || !container.isRunning()) {
            return false;
        }
        container.getConsumer().resume();
        return true;
    }

    /**
     * 采集本服务各消费者的 Broker 最大位点与已提交消费位点差值（积压量）及最老延迟时间。
     *
     * @return 各消费组的积压快照列表
     */
    public List<ConsumerBacklogSnapshot> backlogs() {
        List<ConsumerBacklogSnapshot> snapshots = new ArrayList<>();
        for (DefaultRocketMQListenerContainer container : containers()) {
            long backlog = 0L;
            long oldestSeconds = 0L;
            boolean available = container.getConsumer() != null && container.isRunning();
            if (available) {
                try {
                    for (var queue : container.getConsumer()
                            .fetchSubscribeMessageQueues(container.getTopic())) {
                        long maximum = container.getConsumer().maxOffset(queue);
                        long consumed = container.getConsumer().getOffsetStore().readOffset(
                                queue, ReadOffsetType.MEMORY_FIRST_THEN_STORE);
                        backlog += Math.max(0L, maximum - Math.max(0L, consumed));
                    }
                    if (backlog > 0L) {
                        oldestSeconds = container.getConsumer().getDefaultMQPushConsumerImpl()
                                .queryConsumeTimeSpan(container.getTopic()).stream()
                                .mapToLong(value -> Math.max(0L, value.getDelayTime() / 1000L))
                                .max().orElse(0L);
                    }
                } catch (Exception exception) {
                    available = false;
                    backlog = 0L;
                    oldestSeconds = 0L;
                }
            }
            snapshots.add(new ConsumerBacklogSnapshot(container.getConsumerGroup(),
                    container.getTopic(), backlog, oldestSeconds, available));
        }
        return snapshots.stream()
                .sorted(Comparator.comparing(ConsumerBacklogSnapshot::consumerGroup))
                .toList();
    }

    /**
     * 从 Spring 容器中查找指定消费组的容器实例，校验合法性。
     *
     * @param consumerGroup 消费组名称
     * @return 匹配的 RocketMQ 监听容器实例
     * @throws IllegalArgumentException 若参数为空或不存在对应容器
     */
    private DefaultRocketMQListenerContainer required(String consumerGroup) {
        if (consumerGroup == null || consumerGroup.isBlank() || consumerGroup.length() > 255) {
            throw new IllegalArgumentException("consumerGroup 无效");
        }
        return containers().stream()
                .filter(value -> consumerGroup.equals(value.getConsumerGroup()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("consumerGroup 不属于当前服务"));
    }

    /**
     * 获取当前 Spring 上下文中所有已注册的 RocketMQ 消费者容器实例。
     *
     * @return 监听容器列表
     */
    private List<DefaultRocketMQListenerContainer> containers() {
        return List.copyOf(applicationContext
                .getBeansOfType(DefaultRocketMQListenerContainer.class, false, false).values());
    }

    /** Broker 消费积压快照；不可用时数值不可解释，必须同时查看 available。 */
    public record ConsumerBacklogSnapshot(
            String consumerGroup,
            String topic,
            long backlog,
            long oldestUnconsumedSeconds,
            boolean available
    ) {
    }
}
