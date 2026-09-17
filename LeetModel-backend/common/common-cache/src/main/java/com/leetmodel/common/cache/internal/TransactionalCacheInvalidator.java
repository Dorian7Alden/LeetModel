package com.leetmodel.common.cache.internal;

import com.leetmodel.common.cache.CacheInvalidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 将失效 Outbox 与业务事实放在同一本地事务中。
 */
@Slf4j
public final class TransactionalCacheInvalidator implements CacheInvalidator {

    private final CacheOutboxRepository repository;
    private final CacheCoordinator coordinator;
    private final OutboxDispatcher dispatcher;

    /**
     * 创建事务缓存失效器。
     *
     * @param repository Outbox 仓库
     * @param coordinator 缓存版本协调器
     * @param dispatcher Outbox 投递器
     */
    public TransactionalCacheInvalidator(
            CacheOutboxRepository repository,
            CacheCoordinator coordinator,
            OutboxDispatcher dispatcher
    ) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.dispatcher = dispatcher;
    }

    /**
     * 在当前业务事务内记录失效事件，提交后立即尝试投递。
     *
     * @param region 缓存区域
     * @param scopeKey 失效作用域
     * @param schemaVersion 读模型结构版本
     */
    @Override
    public void record(String region, String scopeKey, String schemaVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("cache invalidation must be recorded in a business transaction");
        }
        CacheInvalidationEvent event = repository.insert(region, scopeKey, schemaVersion);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    coordinator.acceptCommitted(event);
                } catch (RuntimeException exception) {
                    log.error("提交后本地缓存版本更新失败，保留 Outbox 后台重试: eventId={}",
                            event.eventId(), exception);
                }
                dispatcher.dispatch(event);
            }
        });
    }
}
