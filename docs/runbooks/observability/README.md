# 可观测告警 Runbook

本目录是 Prometheus 告警注解指向的版本化处置入口。先确认告警仍在 firing，再按对应 Runbook 调查；不得因为看板暂时无数据就把零值当作恢复，也不得绕过服务所有者接口直接修改业务表。

| 范围 | Runbook |
|------|---------|
| 服务抓取、发现、OAP/Grafana/Alertmanager 与规则失败 | [服务与遥测管道](service-and-telemetry.md) |
| Outbox、Broker 位点、消费积压与 DLQ | [可靠消息](reliable-messaging.md) |
| AI P0-P4 队列、容量与 UNKNOWN | [AI 队列与未知结果](ai-queue-and-unknown.md) |
| 评审、建议、评价、排行和 AI 租约 | [领域租约](domain-leases.md) |

通用恢复证据必须同时包含：告警表达式恢复、对应看板连续两个采集周期正常，以及业务事实或领域任务继续推进。静默只用于已确认的维护窗口，不能作为恢复手段。
