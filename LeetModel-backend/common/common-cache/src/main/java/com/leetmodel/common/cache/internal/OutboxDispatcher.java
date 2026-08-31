package com.leetmodel.common.cache.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 将本地 Outbox 事件幂等应用到 Redis。
 */
@Slf4j
public final class OutboxDispatcher {

    private static final int BATCH_SIZE = 100;

    private final CacheOutboxRepository repository;
    private final CacheCoordinator coordinator;

    /**
     * 创建 Outbox 投递器。
     *
     * @param repository Outbox 仓库
     * @param coordinator 缓存版本协调器
     */
    public OutboxDispatcher(CacheOutboxRepository repository, CacheCoordinator coordinator) {
        this.repository = repository;
        this.coordinator = coordinator;
    }

    /**
     * 每秒重试到期的未投递事件。
     */
    @Scheduled(fixedDelayString = "${leetmodel.cache.outbox-interval:1000}")
    public void dispatchPending() {
        for (CacheInvalidationEvent event : repository.findPending(BATCH_SIZE)) {
            dispatch(event);
        }
    }

    /**
     * 立即投递一个已提交事件。
     *
     * @param event 已提交事件
     */
    void dispatch(CacheInvalidationEvent event) {
        try {
            coordinator.publish(event);
            repository.markDelivered(event.eventId());
        } catch (RuntimeException exception) {
            try {
                repository.markFailed(event.eventId(), exception.getClass().getSimpleName());
            } catch (RuntimeException persistenceException) {
                log.error("缓存失效事件失败状态暂时无法写回: eventId={}", event.eventId(),
                        persistenceException);
            }
            log.warn("缓存失效事件投递失败，将由 Outbox 重试: eventId={}, region={}",
                    event.eventId(), event.region());
        }
    }
}
