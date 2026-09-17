# 全链路故障演练矩阵

入口：`LeetModel-backend/scripts/drill-fault-protection.sh`。默认只校验脚本和输出计划；在已准备好的隔离可观测环境中显式执行 `--live`。不停止标准端口业务进程，不操作固定业务消费组，不修改 `cli-proxy-api`。

| 场景 | 注入/观察 | 受审计恢复动作 | 恢复判据 |
|---|---|---|---|
| OAP 中断/变慢 | 停止隔离 OAP 或使用 fake endpoint，观察 Reporter queue depth、failed/dropped、LOCAL_ROLLING | 只恢复 OAP/Reporter，不重启业务 | 业务请求仍完成；`connected=1`，队列回落，失败可解释 |
| Prometheus/Alertmanager 中断 | 隔离抓取或 webhook，观察 `unavailable`，不把零值当恢复 | 恢复采集/通知组件；必要时用本地 Runbook 查询 | targets、规则评估和 firing/resolved 恢复 |
| Outbox/MQ 积压与 DLQ | 使用临时 namespace/消费组触发 retry、duplicate、DLQ | 通过管理端受控重放原 eventId，生成 `DLQ.REPLAY` 审计 | BLOCKED=0、年龄回落、Inbox 幂等且无正文泄漏 |
| 租约接管 | 隔离 worker 让 lease 过期，观察 normal/takeover 与 fencing | 仅由 owner coordinator 接管，不直接改 lease 字段 | 每次接管有新 attempt/Trace，业务状态不回退 |
| AI UNKNOWN | 使用已有 AI recovery 夹具产生 `AI_UPSTREAM_RESULT_UNKNOWN` | 对账后由管理员受控处置，禁止自动重排 | UNKNOWN 与确定失败分离，存量只因合法收敛下降 |
| 审计消费中断/重复乱序 | 隔离 audit-service/Broker，重投同一 envelope 并打乱阶段到达顺序 | 修复消费者后按原 eventId 重放，保留完整性告警 | Inbox 去重、阶段时间线可解释，缺失阶段不伪装成功 |
| 遥测存储故障 | 让 BanyanDB/LAL 不可用，观察日志本地保留与解析拒绝指标 | 恢复遥测存储，不清空业务日志 | 结构化日志可回查，空洞标记为 unavailable |

每个场景的证据至少包含：固定指标/事件码、故障开始与结束时间、Runbook 链接、受控恢复命令、恢复后的业务事实和审计时间线。演练输出不得包含 Prompt、回答、消息正文、凭据或原始异常堆栈。
