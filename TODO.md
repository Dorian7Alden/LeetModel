# LeetModel TODO

> 本文件只保存当前任务、候选任务和满足条件后才启动的事项。已完成任务和阶段过程不在此保留；已经确认的长期边界与后续触发条件保留在本文件，具体设计和实现结论以 `docs/` 为准。

## 使用约定

- `[ ]` 表示待开始，`[~]` 表示进行中，`[!]` 表示被外部条件阻塞。
- 每次开始前阅读根 `AGENTS.md`、`README.md`、本文件、相关规范、目标模块 README 和任务指向的设计文档。
- 修改公共契约前检查全部生产者和消费者；数据库变更只新增 Flyway 迁移。
- 任务完成至少同步受影响的正式文档并运行目标模块测试；公共模块变化时验证直接消费者。
- 不在配置、日志、测试夹具或文档中保存真实密钥、Relay Token、Prompt、回答正文、知识片段或论文内容。
- new-api Relay Token 由 `ai-gateway-service` 的运行环境提供；具体脱敏和模型复核规则见 [AI 网关测试与验收](docs/project/03-微服务设计/ai-gateway-service/22-测试与验收.md)。
- 模型选型约定：将 New-API 提供的 `gemini-3.8-flash-high` 调整为平台全局首选使用的模型（涵盖客服对话、题目推荐、论文建议、评审及功能测试联调）。
- `cli-proxy-api` 是长期运行且不属于本项目的本地 Docker 服务，固定占用宿主机 `8085`；不得停止、重启、改端口或修改其配置。LeetModel 本地 submission-service 使用 `8092`，启动与验收必须避开该容器。
- 可以自主创建本地阶段分支、执行任务卡原子 commit 和阶段 merge；未经用户明确授权，不执行 push、force push、rebase、破坏性 reset、改写历史、远端分支操作或删除用户文件。

## 当前状态

阶段开发分支 `phase/sentinel-service-protection`：任务卡 1（gateway-service Sentinel 网关流控、统一 429 降级信封与 TraceId 继承）已完成端到端自动化验收与设计沉淀，等待任务卡 2 启动。

## 当前任务

### [ ] 任务卡 2：微服务 Feign 同步调用 Sentinel 熔断降级（common-api 与核心业务服务容错，防御慢调用级联雪崩）

目标：在 `common-api` 开启 OpenFeign 对 Sentinel 断路器的原生支持，为跨微服务同步调用注入慢调用比例与异常比例熔断策略，确保下游故障时通过 FallbackFactory 快速失败，阻断级联雪崩。

入口：`common-api` 中的 Feign 客户端（如 `ProblemFeignClient`、`UserFeignClient`、`TeamFeignClient` 等）。

主流程：
1. 在 `common-api` 或服务调用端开启 `feign.sentinel.enabled=true` 并注入 `SentinelInvocationHandler` / CircuitBreaker 契约。
2. 配置基于慢调用 RT（如 >500ms 且比例超标）与异常比例（如 >50%）的熔断规则（`DegradeRule`）。
3. 审查核心微服务（如 `team-service`、`admin-service` 等）的 FallbackFactory 兜底逻辑，区分安全只读降级与阻断型写降级。
4. 编写自动化集成测试模拟下游长耗时与异常，验证断路器状态机切换（Closed -> Open -> Half-Open）及 Fallback 触发。

完成标准：
1. 下游服务模拟延迟超过 RT 阈值且达到最小请求数时，断路器进入 Open 状态，后续调用直接由本地 Fallback 返回，不再发出真实 HTTP 请求。
2. 熔断窗口期过后，断路器进入 Half-Open 状态进行探测，调用恢复后自动闭合。
3. 全链路 `traceId` 在熔断及 Fallback 期间保持透传与有效性。

修改范围：
- `LeetModel-backend/common/common-api/`
- `LeetModel-backend/team-service/` 等 Feign 消费方配置与测试用例

非目标：
- 不侵入 `ai-gateway-service` 内部的大模型原子任务调度。
- 不影响 RocketMQ 异步可靠传输。

 ---

## 候选任务（Sentinel 阶段演进路线）

- [ ] 任务卡 3：Sentinel 规则动态持久化（Nacos 动态数据源与配置中心联动，实现生产级规则热更新）

全平台微服务（common、user、team、problem、submission、ranking、gateway、admin、audit、knowledge-retrieval、ai-gateway、ai-suggestion、ai-review、ai-assistant、ai-evaluation）代码注释已全量对齐项目级工程规范。

## 已确认的系统边界

- SkyWalking 是唯一 Trace/APM 实现；Prometheus 是指标与告警权威来源；运行日志不能代替操作审计。
- 业务 `traceId` 必须保留并可在 Trace 采样或遥测后端不可用时回退到结构化日志和业务事实。
- audit-service 只负责中央不可变归档与受信只读查询，不拥有用户、题目、提交、AI 任务、消息任务或生产配置等领域规则。
- 管理端通过受权限保护的服务接口执行取消、暂停、恢复、重放和回滚；Grafana、Prometheus、SkyWalking UI 只读观察。
- 遥测后端故障不得阻塞普通业务主链；审计 Outbox 保留并重试，达到严重水位时高风险治理操作 fail-closed。
- 指标、日志、Trace 和审计均遵守低基数、最小数据和脱敏边界，不保存用户/队伍/提交/任务/调用标识作为 Prometheus 标签。
- Sentinel 负责 HTTP 边缘防刷限流与 Feign 慢调用/异常熔断；严禁侵入长耗时 AI 调度和 RocketMQ 消费端。

## 后续触发条件

以下能力暂不拆实现任务；条件成立后先补充任务卡，再进入开发：

- knowledge-retrieval-service 已服务论文建议；客服 RAG 迁移、在线知识管理和独立扩缩容在出现真实消费者或运维需求后再拆任务。
- 单调度器成为可测瓶颈或必须部署多个调度实例后，评估分布式 AI 调度。
- new-api 无法满足渠道治理且出现明确额外业务需求后，评估 LeetModel 多账号资源池。
- 指标具备可信真值且版本选择指数稳定运行后，才讨论自动推荐生产版本；不得自动激活。
- VIP 高级模型分层属于候选能力。进入设计前必须先确认普通与 VIP 在评审、建议、客服三个功能上的模型选择权、配额与计费、降级规则、运行快照和越权防护；未形成统一模型路由契约前不在各业务前端分别硬编码模型。

## 执行规则

1. 每次只选择一个编号任务卡，不直接领取整个阶段。
2. 默认由用户确认任务范围；托管模式下 Agent 按已经确认的路线图和依赖串行推进。
3. 任务依赖未满足时不得用临时硬编码绕过，应明确标记阻塞。
4. 新发现的问题若不阻断当前闭环，只记录为候选或条件任务，不扩大当前任务。
5. 任务完成后，只把后续仍有效的决策与边界合并到所属正式文档，然后删除已完成任务卡；不保存完成历史或单独的阶段过程归档。
