# 遥测与审计故障保护 Runbook

本 Runbook 处理 OAP/Prometheus/日志 Reporter 或审计 Outbox 不可用，不把遥测空洞误判成业务成功。

## 遥测管道故障

1. 先看 `leetmodel_logging_reporter_events_total` 的 `failed`、`dropped`、`queue_depth` 和 `connected`；确认本地 `LOCAL_ROLLING` 仍在写入。
2. OAP 或 Prometheus 故障期间，业务线程不执行网络重试；Reporter 只使用有界队列、单 daemon 线程、固定超时和有限尝试次数。
3. 队列满时优先丢弃 INFO/DEBUG；高优先级丢弃必须通过 `dropped{cause="queue_full_high_priority"}` 暴露，不能补写成成功。
4. 恢复后确认 `connected=1`、`recovered_total` 增长，再以本地日志和业务事实补齐故障窗口；Trace/指标缺失写明 `unavailable`。

## 审计 Outbox 阻塞

1. 查看来源服务 `leetmodel.messaging.outbox.records{status="blocked"}`、最老年龄和 audit-service/Broker 状态；不要直接改 `message_outbox`。
2. `OUTBOX.REPLAY`、Consumer 暂停/恢复、评价控制和 AI 生产配置切换等高风险命令在本地审计 Outbox 出现 `BLOCKED` 时 fail-closed；普通业务继续按既有规则运行。
3. 修复编解码、Topic/ACL 或消费者后，使用受控管理接口按 Runbook 重放原事件；恢复动作自身必须产生新的操作审计事件。
4. 审计查询页遇到中央或来源不可用时显示数据空洞，不以空列表、零值或 HTTP 成功代替事实。

## 恢复判据

- Reporter 队列深度回到稳定范围、`connected=1` 且失败不再增长。
- Outbox `BLOCKED=0`，待发送年龄回落，audit-service 归档延迟和 DLQ 告警恢复。
- 高风险命令可在管理端重新执行并得到阶段完整的审计时间线。
