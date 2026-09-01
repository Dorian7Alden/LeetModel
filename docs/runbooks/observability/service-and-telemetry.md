# 服务与遥测管道 Runbook

## LeetModelServiceMetricsUnavailable

影响：Prometheus 无法抓取某个服务。它既可能是服务不可用，也可能只是管理 Token、端口或采集链路故障，因此不能只凭 `up=0` 重启业务。

1. 在 Prometheus Targets 查看 `lastError`，区分连接拒绝、超时、HTTP 403 和格式错误。
2. 直接请求该服务 `/actuator/health/liveness` 与 `/readiness`。Liveness DOWN 才表示进程应重启；Readiness DOWN 先检查本地数据库和启动迁移。
3. HTTP 403 时核对 `.observability-runtime/management-token` 与业务进程 `MANAGEMENT_TOKEN` 是否来自同一次部署，不在日志或工单粘贴 Token。
4. 端点成功但 Prometheus 失败时检查 `prometheus.yml`、file-SD target、loopback 绑定和 Prometheus 日志。
5. 恢复标准：`up=1` 持续两个采集周期，Readiness 正常，并用一条只读用户主链请求确认服务不是“只剩指标端点”。

禁止：基于单条 Outbox、Redis 降级或 `up=0` 盲目循环重启；把 Actuator 加入 Gateway 公网路由；临时关闭管理 Token。

## LeetModelServiceDiscoveryIncomplete

影响：版本化发现集合少于 13 个服务，缺失目标不会产生普通的逐服务 `up=0`。

1. 对比 `docker/observability/prometheus-targets/leetmodel-services.json` 与当前 13 个服务清单，确认文件仍有 13 个唯一 `service` 标签。
2. 检查 Prometheus `/service-discovery` 的 file-SD 错误与容器内 `/etc/prometheus/targets` 挂载。
3. 使用 `./scripts/verify-observability-stack.sh --static` 验证目标集合与配置。
4. 恢复标准：`leetmodel:service_targets:count` 回到至少 13，且缺失服务重新表现为可解释的 `up=0/1`。

## LeetModelTelemetryComponentUnavailable

影响：OAP、Grafana 或 Alertmanager 的某项观察能力不可用；业务必须保持 fail-open。

1. 查看遥测管道看板和对应容器 health/log，不从业务日志推断遥测组件状态。
2. OAP 故障时保留业务 stdout，本阶段不承诺中央日志；Grafana 故障时直接使用 Prometheus 查询；Alertmanager 故障转到下一节。
3. 只恢复故障组件，不停止业务服务或清空命名卷。
4. 恢复标准：组件 `up=1`、抓取样本重新增长，相关 UI/API 可查询。

## LeetModelAlertmanagerDisconnected

影响：Prometheus 仍会计算规则，但分组、抑制、静默与通知失效。

1. 检查 `prometheus_notifications_alertmanagers_discovered`、Prometheus alertmanager discovery 状态和 Alertmanager `/-/ready`。
2. 运行 `amtool check-config`，检查 Alertmanager host-network 的 `127.0.0.1:19093` 监听，不把端口改到公网地址。
3. 恢复后执行 `./scripts/drill-alerting.sh`，确认 firing 与 resolved 通知都到达隔离 webhook。
4. 恢复标准：discovered 至少为 1，Alertmanager ready，隔离演练完整通过。

## LeetModelRuleEvaluationFailure

影响：部分记录或告警规则可能没有输出；缺失序列不能解释为正常。

1. 在 Prometheus `/rules` 找到失败组和错误，保留原表达式与错误信息。
2. 使用容器内 `promtool check config` 和 `./scripts/verify-alerting-contract.sh` 复现。
3. 检查指标名、标签匹配和多对多向量运算；不要通过删除失败规则掩盖空洞。
4. 恢复标准：修正规则后连续两个评估周期无新增 `prometheus_rule_evaluation_failures_total`。
