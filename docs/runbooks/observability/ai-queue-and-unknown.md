# AI 队列与未知结果 Runbook

AI 队列按可信来源重算 P0-P4；处置不能通过修改优先级让后台任务抢占 P0，也不能在 LeetModel 复制 new-api 的渠道重试。

## LeetModelAiP0QueueWaitHigh

影响：P0 客服 Chat/RAG Query 在 8 秒预警，10 秒达到最大排队时间。

1. 在 AI 看板确认 P0 queued/leased/running、最老等待、全局 executor 和上游限流。
2. 检查总并发 4 与 P0 保留 1 个许可是否仍生效；暂停 P3/P4 批任务，不抢占已运行请求。
3. new-api 返回 429 时尊重最多 30 秒的 `Retry-After` 退避，不在 LeetModel 添加透明重试。
4. 恢复标准：P0 最老等待低于 8 秒，新的 P0 调用正常完成，EXPIRED/QUEUE_FULL 不再增长。

## LeetModelAiBackgroundQueueWaitHigh

影响：P1/P2/P3/P4 达到各自 60 秒、30 秒、5 分钟、10 分钟排队期限的 80%。

1. 核对公平调度槽位、老化和 deadline；P3/P4 可暂停，P1 正式评审/建议不能被批任务挤占。
2. 检查数据库领取、executor 拒绝与上游限流，不通过手工改 `effective_priority` 恢复。
3. 恢复标准：对应优先级低于 80% 时限且任务持续完成。

## LeetModelAiQueueCapacityHigh

影响：活跃任务达到总容量 500 的 80%，新任务接近 `AI_QUEUE_FULL`。

1. 按优先级和状态拆分 400 个活跃任务，确认是否由单个后台来源造成。
2. 优先暂停 P3/P4 生产者；不要简单提高单实例并发或容量上限，当前值未外推为生产能力。
3. 恢复标准：活跃任务低于 400，P0 延迟和准入拒绝同时恢复。

## LeetModelAiUpstreamResultUnknown

影响：attempt 已进入 `DISPATCHING/ACKNOWLEDGED` 后失去确定结果，上游可能已执行并计费。

1. 从管理端查询任务、attempt、稳定错误和 new-api 请求 ID；只读对账上游，不记录 Prompt、回答或 Token。
2. 明确区分无 attempt/`PREPARED` 的安全重排与 `UNKNOWN`。后者永远禁止自动重排。
3. 由业务 owner 根据上游事实决定补偿；管理员不得改数据库、删除 attempt、清空错误或复制幂等键。
4. 恢复标准：每个 UNKNOWN 都有对账结论和业务处置；指标存量归零只能来自合法状态收敛，不得伪造成功。
