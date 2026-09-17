# 可靠消息 Runbook

调查前先确认 `leetmodel.messaging.consumer.metrics.available` 与 `dlq.metrics.available`。`available=0` 时 backlog/DLQ 的零值不可解释，先恢复 Broker 管理读链路。

## LeetModelOutboxPublishDelayed

影响：业务事务已提交，但事件没有及时形成下游 Inbox 与领域任务。30 秒是首期警告水位，5 分钟是严重水位，不是吞吐容量承诺。

1. 在可靠消息看板按 `service/status` 查看 PENDING、SENDING、BLOCKED 数量与最老年龄。
2. 通过管理端只读接口检查 Relay、Broker、下次重试时间和租约；查看 `publish{outcome=retry|blocked}`，不要查询或导出 Payload。
3. Broker 不可用时等待 Outbox 退避恢复。租约卡住时先确认旧 owner 已退出，允许条件领取接管。
4. 严重水位时暂停 BATCH；在线主链保留论文提交，按既有规则限制新的建议任务。
5. 恢复标准：最老 PENDING/SENDING 小于 30 秒，Outbox 继续发布，对应 Inbox 和领域任务真实推进。

禁止：绕过 Outbox 直接发新消息；删除 PENDING；使用新 eventId 重做同一事实。

## LeetModelOutboxBlocked

影响：Topic、schema、序列化或稳定配置错误使 Relay 停止快速重试，无法自动恢复。

1. 从管理端查看脱敏错误分类，核对 Topic、Tag、schema 与发布契约；不要把原 Payload 复制到日志或告警。
2. 修复配置或代码，并用相同契约完成测试。
3. 通过服务所有者的受控补发恢复原 Outbox，保留原 `eventId`；一次最多 20 条并填写原因。
4. 恢复标准：BLOCKED=0，发布成功，原事件在下游 Inbox/领域事实中收敛。

## LeetModelOnlineConsumerBacklogHigh

影响：评审或建议唤醒延迟。200/1000 是 ONLINE_CORE 初始警告/严重水位。

1. 同时检查 `metrics.available=1`、Topic backlog、消费进程 running/paused、Inbox 失败率和领域最老等待。
2. 警告时暂停 BATCH 或降低后台并发，保留 P0/在线 Worker 许可。
3. 严重时限制新建议任务但继续接受可持久化的论文提交；检查数据库锁、Broker 和消费者短事务。
4. 恢复标准：backlog 小于 200，Inbox 与领域任务持续推进，未通过移动 offset 丢弃消息。

## LeetModelOnlineConsumerOldestHigh

影响：ONLINE_CORE 的下一条未消费消息等待达到 2 分钟警告或 10 分钟严重水位。该值来自 Broker 对消费位点下一条消息的 `delayTime`，必须和 backlog 及 `metrics.available` 联合解释。

1. 按 Topic/消费组比较最老年龄与 backlog；数量低但年龄高通常表示消费者停止或单条消息持续失败。
2. 检查消费容器 running/paused、Inbox 短事务和 Broker 重试，不在消费线程执行 AI 或长任务。
3. 警告时暂停 BATCH；严重时限制新建议任务并保留可持久化论文提交。
4. 恢复标准：最老年龄小于 2 分钟，原事件进入 Inbox 与领域任务；不得移动 offset 或跳过消息伪造恢复。

## LeetModelBrokerMetricsUnavailable

影响：消费积压和 DLQ 事实无法读取；展示的零只是占位值。

1. 检查消费容器是否 running、Broker 管理读权限、NameServer/Broker 连通性和指标刷新任务。
2. 不基于 backlog=0 恢复批任务；先让 consumer 与 DLQ 的 `metrics.available` 都回到 1。
3. 恢复标准：可用性为 1，随后一次真实刷新返回可解释数值。

## LeetModelDeadLetterPresent

影响：至少一个事件未形成目标领域事实。任一 DLQ 条目立即警告，10 分钟内持续增长升级为严重。

1. 按 eventId 关联原 Outbox、Inbox、领域任务和脱敏错误，判断瞬时故障还是稳定 schema/不变量错误。
2. 先修复根因并验证同类消息可处理，再由管理员填写原因，单条或最多 20 条受控补发原 Outbox。
3. 保留原 `eventId`、幂等键和历史；DLQ 永不自动回灌。永久无效消息必须记录原因后归档。
4. 恢复标准：不再新增死信，存量已受控恢复或归档，领域事实已确认；“已重新发送”本身不算恢复。

### 操作审计专用 DLQ

`%DLQ%<namespace>%cg-audit-archive-v1` 中的消息是尚未进入中央不可变归档的权威操作事实，优先级高于普通派生事件。先核对 `auditEventId`、schema、操作目录与白名单错误，不查看或导出完整消息正文；修复 consumer 后以原 `auditEventId` 重放，批准重放的人工动作另生成新的 `DLQ.REPLAY` 操作审计。禁止修改原信封、换新 eventId、移动 offset 丢弃、自动回灌或用运行日志替代缺失归档。ACL 故障时分别验证生产账号只有 Topic `Pub`、archive 账号只有该 Topic 与消费组 `Sub`，不要临时授予 `Topic:*` 或 Super 权限。
