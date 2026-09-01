# 领域租约 Runbook

## LeetModelDomainLeaseExpired

影响：评审、建议、评价、排行或 AI 调度 Worker 失去 heartbeat。出现一个租约先警告；同类连续 10 分钟仍存在升级为严重，表示自动恢复未收敛。

1. 在异步任务看板确认服务、过期租约、最老等待、claim_type 与 attempt 结果。
2. 检查 Worker 进程、数据库连接、heartbeat 调度和恢复扫描；先确认旧 owner 已退出或已失去 fencing token。
3. 无外部副作用的过期租约允许条件接管并产生新 attempt；旧 owner 后续写入必须因 fencing 失败。
4. 评价过期 RUNNING 进入 `takeover_unknown`；AI `DISPATCHING/ACKNOWLEDGED` 进入 `AI_UPSTREAM_RESULT_UNKNOWN`，禁止自动重做。
5. 恢复标准：过期租约归零，任务进入可解释终态或新 attempt 正常运行，旧 owner 不能覆盖新结果。

禁止：直接清空 lease owner/version；把 UNKNOWN 改回 WAITING/QUEUED；仅重启进程而不核验 fencing 和业务事实。
