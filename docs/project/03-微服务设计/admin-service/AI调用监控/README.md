## AI 调用监控

> S5 已提供 AI 调用审计、只读队列查询和受控取消入口；admin-service 只代理网关契约，不访问网关数据库。

### 定位

AI 调用监控用于管理员查看平台内的 AI 使用情况，关注资源消耗、调用稳定性和异常。


### 职责

- 按供应商、模型、场景和调用服务统计数据。
- 展示 Token、成本、排队与执行耗时、成功率和失败情况。
- 区分正式评审调用与稳定性实验产生的评审调用。
- 支持从业务任务追踪到单次模型调用。


### 边界

- AI 网关拥有单次调用、Token、成本、延迟和失败数据。
- admin 服务负责聚合和展示，不直接访问 AI 网关数据库。
- 该模块只展示 AI 网关运行事实，不计算稳定性、质量、归一化、权重或版本选择指数。

### 队列运维接口

| 管理员接口 | 用途 |
|------------|------|
| `GET /api/admin/ai/calls` | 查询调用记录；评价页面使用 `evaluationTaskId` 追踪到单次 `callId` |
| `GET /api/admin/ai/calls/stats` | 按同一组结构化条件统计调用资源与状态 |
| `GET /api/admin/ai/queue` | 查询队列元数据；支持 `state`、`priority`、`callerService`、`evaluationTaskId`、`minWaitMs`、`limit` |
| `POST /api/admin/ai/queue/{taskId}/cancel` | 条件取消 `QUEUED/LEASED/RUNNING` 任务 |

查询最多返回 100 条。响应只包含 `taskId/callId`、来源、调用类型、feature/operation、有效优先级、状态、attempt 次数、取消标记、稳定错误、dead-letter 原因、排队耗时和阶段时间。不得增加 request/result payload、Prompt、回答、论文、知识片段、图片、向量、幂等键或请求哈希。接口受管理员角色保护；网关内部接口仍只供服务间调用。

排队任务取消后立即进入 `CANCELLED`；`LEASED` 任务通过条件更新阻止派发；`RUNNING` 任务只记录取消意图并停止向等待方交付，不能声称已取消 new-api 计费。并发终态变化导致取消失败时返回 `AI_TASK_NOT_CANCELLABLE`，管理员应刷新状态，不得直接改数据库。

### 告警建议

- P0 等待达到 8 秒预警、达到 10 秒或出现 `EXPIRED` 严重告警；P1/P2/P3/P4 分别以其 60 秒、30 秒、5 分钟、10 分钟最大排队时间的 80% 预警。
- 活跃任务接近总容量 500 或非 P0 容量 450 的 80% 时预警，达到上限且出现 `AI_QUEUE_FULL` 时严重告警。
- 任一 `AI_UPSTREAM_RESULT_UNKNOWN` 立即严重告警；它代表可能已产生费用，不能自动重试。
- 连续 `UPSTREAM_RATE_LIMITED` 或调度退避反复达到 30 秒时告警，先核验 new-api 渠道、配额和 `Retry-After`，不要在 LeetModel 增加重试。
- P3/P4 长时间没有完成记录时同时检查 P0 洪峰、数据库健康和调度器日志；公平调度保证执行机会，但不承诺后台 deadline 外完成。

### 恢复处置

1. 数据库短故障先恢复数据库；网关不会绕过持久化直调 new-api。
2. `QUEUED` 任务自动继续；无 attempt 或 `PREPARED` 的过期租约由恢复器安全重排。
3. `DISPATCHING/ACKNOWLEDGED` 后失去 owner 的任务进入 `FAILED + AI_UPSTREAM_RESULT_UNKNOWN`。如有 new-api 请求 ID，只做只读对账并由业务 owner 决定补偿；禁止直接重排或复制幂等键。
4. 明确终态只从查询入口核验。人工处置不得删除 attempt、清空错误或把终态改回 `QUEUED`。
