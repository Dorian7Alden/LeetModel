## common-messaging

### 模块定位

`common-messaging` 是不含业务语义的可靠消息公共 Jar。它提供 `MessageEnvelopeV1<T>`、UUID/ULID 与 64 KiB 契约校验、环境 namespace、事务 Outbox、短租约 Relay、RocketMQ Spring 发布适配器、事务 Inbox、消息关联上下文、低基数指标、健康检查和 `RecordingMessagePublisher` 测试替身。

`MessageEnvelopeV1` 固定保存 `eventId` 和 `traceId`，并以可选 `operationId` 连接人工治理命令。该可选字段向后兼容，旧信封解码为 null。`MessageCorrelationContext` 只在信封校验后恢复 Trace、Operation、Event 与明确的任务 attempt，作用域关闭时恢复消费线程 MDC。

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

配置有范围校验并在启动时输出 namespace、批量、租约和消息上限摘要。Topic、Tag、消费组和事件类型属于发布契约，不提供运行时动态改名能力。`messagingHealthIndicator` 在出现 `BLOCKED` 消息时返回 `DEGRADED`，使运维可观测但不污染 Liveness。

Micrometer 指标覆盖以下稳定事实：

| 指标 | 维度与语义 |
|------|------------|
| `leetmodel.messaging.outbox.records` / `oldest.seconds` | 固定 `PENDING/SENDING/PUBLISHED/BLOCKED` 状态的数量与最老年龄 |
| `leetmodel.messaging.outbox.claims` | 固定 Topic 下的 `normal/takeover` 领取；过期 `SENDING` 重新领取单独计数 |
| `leetmodel.messaging.publish` / `publish.duration` | 固定 Topic 下的 `success/retry/blocked` 吞吐与 Relay 耗时 |
| `leetmodel.messaging.inbox.records` / `oldest.processing.seconds` | `PROCESSING/CONSUMED` 状态与未完成短事务年龄 |
| `leetmodel.messaging.consume` / `consume.duration` | 本地消费组的 `consumed/duplicate/failure` 吞吐与短事务耗时 |
| `leetmodel.messaging.consumer.backlog` / `consumer.oldest.seconds` | 本地消费组与固定 Topic 的 Broker 最大位点减消费位点，以及消费位点下一条消息的最老等待时间 |
| `leetmodel.messaging.dlq.records` / `dlq.oldest.seconds` | 本地消费组对应 `%DLQ%ConsumerGroup` 的存量与最老消息年龄 |

Broker 位点与 DLQ 查询各有 `*.metrics.available` 仪表。读取失败时数值为不可解释的占位值，必须与 `available=0` 联合判断，不能把不可用解释为零积压。指标标签不包含 `eventId`、`traceId`、`operationId`、消息 Key 或 Payload。

### MQ6 运维边界

启用模块的服务会暴露 `/internal/messaging` 内网契约，返回脱敏 Outbox、Inbox、领域积压、真实 consumer 运行状态和 Broker DLQ 摘要。consumer 暂停/恢复直接调用 RocketMQ Push Consumer 的 `suspend`/`resume`；Outbox 补发只接受 `PUBLISHED` 或 `BLOCKED` 的原 eventId，最多 20 条，不生成新业务事件。

DLQ 查询使用现有生产者连接的 Broker 管理读接口读取 `%DLQ%ConsumerGroup`，不会创建 DLQ 消费者或移动 offset。公共模块只负责精确定位死信并解码信封元数据；实际重放由 admin-service 校验完整 eventId 集合后，委托信封中的来源服务重置原 Outbox。DLQ 永不自动回灌，所有写操作都由管理员入口提供原因并形成操作结果。

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
