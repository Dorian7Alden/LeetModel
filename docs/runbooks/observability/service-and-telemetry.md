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

影响：OAP、OAP LAL 指标出口、Grafana 或 Alertmanager 的某项观察能力不可用；业务必须保持 fail-open。

1. 查看遥测管道看板和对应容器 health/log，不从业务日志推断遥测组件状态。
2. OAP 故障时先确认业务 stdout/本地轮转仍增长，再查询 `leetmodel_logging_reporter_events_total` 的 `failed/dropped`、队列深度和 `connected`；Grafana 故障时直接使用 Prometheus 查询；Alertmanager 故障转到下一节。
3. `skywalking-log-metrics` 故障时，LAL 拒绝计数形成遥测空洞，但 OAP 日志接收与业务链不应中断；检查只读日志卷、13903 loopback 监听和 mtail 程序编译，不授予容器 Docker socket。
4. 只恢复故障组件，不停止业务服务或清空命名卷。禁止通过扩大 Reporter 队列或无限重试掩盖后端故障。
5. 恢复标准：组件 `up=1`、抓取样本重新增长；Reporter `connected=1` 且 `recovered` 增长；使用脱敏测试标记在 GraphQL 查询一条新日志。

## SkyWalking Trace 缺失或分段

影响：业务请求可能成功，但某次物理执行不能在 OAP 中形成完整拓扑。Trace 可采样且不是业务事实，不能因为 Trace 缺失重做请求或外部副作用。

1. 先用响应 `X-Trace-Id`、结构化日志 `traceId` 或领域事实确认请求是否真实成功；不要把 OAP 空结果解释成业务失败。
2. 检查进程是否以 `LEETMODEL_SKYWALKING_ENABLED=true` 启动，以及 Agent service、namespace、instance、environment、serviceVersion 和 `sample_n_per_3_secs` 是否为本次发布值。`LEETMODEL_SKYWALKING_SAMPLE=0` 或负数表示关闭每三秒限流、即全量采集，不表示关闭 Agent。
3. OAP 查询在 segment 到达期间可能先返回 Gateway 局部 Span；等待两个上报周期后再确认是否同时存在 Gateway Exit、下游 MVC Entry、Feign Exit 和 JDBC Span。
4. Feign 缺失时确认 `feign-default-http-9.x,feign-pathvar-9.x` 已排除，公共 `SkyWalkingFeignCapability` 已装配；不得临时启用不兼容旧插件或增加第二套 exporter。
5. RocketMQ 5.3.1 仅承诺生产端自动 Exit Span；消费与 Inbox 使用 `Messaging/InboxConsumeAttempt`，Worker 领取/接管、AI provider attempt 和恢复判定使用项目固定 Entry operation。不能把没有自动 Consumer Entry 当成 Broker 丢消息。
6. 若任务出现异常长 Span，对比 Span 起止与数据库 `queuedAt/leaseExpiresAt/attemptNo`。排队等待和租约间隙不得进入 Span；接管必须拥有不同 `swTraceId` 和递增 attempt，禁止手工续接上一任 Trace。
7. 使用 `./scripts/verify-skywalking-tracing.sh` 与 `./scripts/verify-skywalking-async.sh` 做静态检查；隔离环境运行对应的 `--runtime`。异步门禁创建并清理精确临时消费组，在 OAP 核对 Outbox 成功/重试、Inbox consumed/duplicate、正常/接管和 AI UNKNOWN；不得操作固定业务消费组。
8. 使用 `./scripts/drill-observability-correlation.sh` 在隔离环境验证 Outbox backlog 与 AI UNKNOWN 的告警 → operation → 中央日志 → 事实闭环。该脚本只读 OAP/日志与临时 H2 事实，并在 Trace 尚未到达、被采样或 Reporter 不可用时输出 `sampled_or_not_found/unavailable` 空洞和业务 `traceId` 回退路径。
9. 恢复标准：新请求在同一 Trace 中出现完整同步链，每个异步物理 attempt 是独立有界 Trace，UNKNOWN 与确定失败可区分；中央日志可分别按 `business_trace_id` 与 `sw_trace_id` 查到同一 JSON 记录；OAP 中断时业务请求仍成功。

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
