package com.leetmodel.common.messaging.testing;

import com.leetmodel.common.messaging.MessagePublisher;
import com.leetmodel.common.messaging.PendingMessage;
import com.leetmodel.common.messaging.PublishReceipt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 供服务单元测试替换真实 Broker 的内存发布器。
 */
public final class RecordingMessagePublisher implements MessagePublisher {

    private final CopyOnWriteArrayList<PendingMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public PublishReceipt publish(PendingMessage message) {
        messages.add(message);
        return new PublishReceipt("recording-" + message.eventId());
    }

    /**
     * 返回不可变的已发布消息快照。
     *
     * @return 已发布消息
     */
    public List<PendingMessage> messages() {
        return List.copyOf(messages);
    }

    /**
     * 清空已记录消息。
     */
    public void clear() {
        messages.clear();
    }
}
