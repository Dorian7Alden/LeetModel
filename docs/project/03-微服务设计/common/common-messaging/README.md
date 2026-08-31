## common-messaging

### 模块定位

`common-messaging` 是不含业务语义的可靠消息公共 Jar。它提供 `MessageEnvelopeV1<T>`、UUID/ULID 与 64 KiB 契约校验、环境 namespace、事务 Outbox、短租约 Relay、RocketMQ Spring 发布适配器、事务 Inbox、低基数指标、健康检查和 `RecordingMessagePublisher` 测试替身。

模块保证至少一次发布和消费端业务一次效果的基础条件，不承诺端到端恰好一次。MQ 消费线程必须只执行契约校验、Inbox 去重和领域任务落库；PDF 处理、AI 调用、知识检索和排行全量重建仍由带租约的领域 Worker 执行。

### 事务边界

生产端在业务服务自己的 `@Transactional` 方法中先写业务事实，再调用 `MessageOutbox.enqueue`。两项写入使用同一数据源与事务；Broker 暂时不可用不会回滚已完整保存的业务事实，Relay 会按 1 秒、5 秒、30 秒、2 分钟、10 分钟和之后每 30 分钟的策略持续退避。

消费端先通过 `MessageCodec.decode` 校验消息，再调用 `MessageInbox.executeOnce`。Inbox 唯一键为 `consumer_group + event_id`，首次消息的 Inbox 与调用方短事务动作一起提交；动作抛异常时两者一起回滚，重复消息返回 `DUPLICATE`。不要在 `domainAction` 中执行远程调用或长计算。

### 数据表契约

每个生产服务通过自己的 Flyway 迁移创建 `message_outbox`，至少包含事件、物理 Topic、Tag、Key、契约字段、JSON、`PENDING/SENDING/PUBLISHED/BLOCKED` 状态、重试时间、租约、Broker messageId、错误摘要和审计时间，并建立 `(status, next_attempt_at, lease_expires_at, create_time)` 索引。

每个消费服务创建 `message_inbox`，保存消费组、eventId、事件类型、来源、`PROCESSING/CONSUMED` 状态与时间，并对 `(consumer_group, event_id)` 建立唯一约束。公共测试所用参考结构位于后端模块的 `src/test/resources/messaging-schema.sql`；业务服务必须复制为自己的版本化 Flyway 文件，不能依赖测试资源自动建表。

### 默认配置

模块默认禁用，业务服务完成 Flyway 后才可显式启用：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: lm-dev%pg-submission-v1
    # access-key 与 secret-key 只通过环境变量或配置中心提供

leetmodel:
  messaging:
    enabled: true
    namespace: lm-dev
    max-payload-bytes: 65536
    send-timeout-ms: 3000
    relay:
      enabled: true
      batch-size: 50
      interval-ms: 1000
      lease-seconds: 30
```

配置有范围校验并在启动时输出 namespace、批量、租约和消息上限摘要。Topic、Tag、消费组和事件类型属于发布契约，不提供运行时动态改名能力。`messagingHealthIndicator` 在出现 `BLOCKED` 消息时返回 DOWN；Micrometer 暴露发布结果、消费结果、Outbox 状态数和最老待发送年龄，标签不包含 eventId 等高基数字段。

### 本地验证

```bash
cd LeetModel-backend
docker compose up -d --wait rocketmq-namesrv rocketmq-broker
./scripts/init-rocketmq.sh
./scripts/verify-rocketmq.sh
mvn -pl common/common-messaging test
RUN_ROCKETMQ_INTEGRATION=true mvn -pl common/common-messaging test
```

最后一条命令通过 RocketMQ Spring 2.3.3 发布器真实发送消息，以预创建消费组接收同一 eventId 的两次投递并验证 Inbox 只执行一次，同时制造一次短暂消费失败并确认 `reconsumeTimes=1`。`ROCKETMQ_VERIFY_RESTART=true ./scripts/verify-rocketmq.sh` 会额外重启 Broker 并按 Key 验证消息仍可查询。
