# LeetModel TODO

> 本文件只保存当前任务、候选任务和满足条件后才启动的事项。已完成任务和阶段过程不在此保留；仅将后续仍有效的决策与边界写入对应的正式 `docs/` 文档。

## 使用约定

- `[ ]` 表示待开始，`[~]` 表示进行中，`[!]` 表示被外部条件阻塞。
- 每次开始前阅读根 `CONTEXT.md`、`README.md`、本文件、相关规范、目标模块 README 和任务指向的设计文档。
- 修改公共契约前检查全部生产者和消费者；数据库变更只新增 Flyway 迁移。
- 任务完成至少同步受影响的正式文档并运行目标模块测试；公共模块变化时验证直接消费者。
- 不在配置、日志、测试夹具或文档中保存真实密钥、Relay Token、Prompt、回答正文、知识片段或论文内容。
- new-api Relay Token 由 `ai-gateway-service` 的运行环境提供；具体脱敏和模型复核规则见 [AI 网关测试与验收](docs/project/03-微服务设计/ai-gateway-service/22-测试与验收.md)。
- `cli-proxy-api` 是长期运行且不属于本项目的本地 Docker 服务，固定占用宿主机 `8085`；不得停止、重启、改端口或修改其配置。LeetModel 本地 submission-service 使用 `8092`，启动与验收必须避开该容器。
- 可以自主创建本地阶段分支、执行任务卡原子 commit 和阶段 merge；未经用户明确授权，不执行 push、force push、rebase、破坏性 reset、改写历史、远端分支操作或删除用户文件。

## 当前任务

### [ ] MET-02 核心业务与异步指标

目标：在统一 Actuator 基线上补齐低基数、可聚合的业务与异步指标，使 HTTP、可靠消息、领域任务和 AI 调度链路能够被持续量化。

入口：公共 Web/Messaging/Micrometer 能力、Outbox/Inbox 与 MQ 实现、领域任务租约、`ai-gateway-service` P0-P4 队列与计量审计、各服务线程池和 HikariCP 配置。

主流程：统一 HTTP RED 与路由模板标签 → 接入 JVM、线程池和 HikariCP 指标 → 补齐 Outbox/Inbox/MQ 吞吐、积压、最老年龄、重试、重复与 DLQ 指标 → 补齐领域任务租约、接管与结果指标 → 补齐 AI P0-P4 排队、执行、端到端耗时、Token、费用和 UNKNOWN 指标 → 验证全部标签基数与失败分类。

完成标准：公共 HTTP、JVM、线程池和 HikariCP 指标可用；可靠消息能区分重复消费、失败、积压与 DLQ；领域任务能区分租约接管；AI 指标覆盖 P0-P4、排队/执行/端到端耗时、Token、费用和 UNKNOWN；自动化测试证明指标标签不包含用户、队伍、提交、trace、operation、event、task 或 AI Call ID，HTTP 路由只使用模板。

修改范围：后端公共指标契约与埋点、直接生产者和消费者、AI 网关计量与调度指标、必要的测试与正式文档。

非目标：不在本任务引入 Prometheus/Grafana/Alertmanager 编排和告警规则，不接入 SkyWalking Agent，不改造 JSON 日志或中央审计，不以高基数业务标识作为指标标签。

## 系统保障实施路线图

设计依据：

- [可观测性与系统保障](docs/project/02-架构设计/可观测性与系统保障.md)
- [日志系统](docs/project/02-架构设计/日志系统.md)
- [操作审计架构](docs/project/02-架构设计/操作审计架构.md)
- [audit-service 服务边界](docs/project/03-微服务设计/audit-service/README.md)

全程遵守以下边界：SkyWalking 是唯一 Trace/APM 实现，Prometheus 是指标与告警权威来源；现有业务 `traceId` 必须保留；运行日志不能代替操作审计；audit-service 不拥有领域规则；长耗时 AI 不建立跨分钟长 Span；遥测后端故障不得阻塞业务主链。


### 阶段 0：实施基线与公共约束

### 阶段 1：Metrics、健康检查与主动告警

#### [ ] MET-02 核心业务与异步指标

- 依赖：`MET-01`、`OBS-02`。
- 范围：统一 HTTP RED、JVM、线程池、HikariCP、Outbox/Inbox/MQ、领域任务租约、AI P0-P4 队列、排队/执行/端到端耗时、Token/费用和 UNKNOWN 指标。
- 验收：标签不包含用户、队伍、提交、trace、operation、event、task 或 AI Call ID；路由使用模板；重复消费、租约接管和 UNKNOWN 均能在指标中区分。

#### [ ] MET-03 Prometheus、Grafana 与 Alertmanager

- 依赖：`MET-01`。
- 范围：加入环境编排、抓取配置、规则加载、通知路由和基础设施自身监测；管理端点不经公网 Gateway 暴露。
- 验收：建立系统总览、MVP 主链、AI 资源、异步任务、可靠消息和遥测管道看板；Prometheus 中断不影响业务。

#### [ ] MET-04 告警规则与 Runbook 闭环

- 依赖：`MET-02`、`MET-03`。
- 范围：落地 MQ/Outbox 最老年龄、DLQ、AI P0 等待、AI UNKNOWN、服务不可用和遥测空洞告警；HTTP SLO 在取得基线后设定。
- 验收：每条严重告警包含影响、当前值、看板、调查入口、Runbook 和恢复条件；完成分组、抑制、静默及恢复通知验证。


### 阶段 2：结构化日志系统

#### [ ] LOG-01 全服务统一 JSON 日志

- 依赖：`OBS-02`。
- 范围：提供可版本化 Logback JSON 规范，统一时间、服务资源、Trace、HTTP、业务关联、异步 attempt 和异常字段；保留 stdout 与本地轮转兜底。
- 验收：全部服务输出同 schema；生产默认不输出 DEBUG、SQL 参数、完整请求响应或高频 heartbeat INFO。

#### [ ] LOG-02 脱敏、注入防护与日志限频

- 依赖：`LOG-01`。
- 范围：集中处理 Token/Cookie/凭据、论文、Prompt、回答、知识片段、消息 Payload、第三方 URL 和异常摘要；清理 CR/LF 并限制字段长度；重复故障日志限频聚合。
- 验收：负面测试证明敏感内容和日志注入无法进入输出；积压和依赖故障不会形成日志风暴。

#### [ ] LOG-03 SkyWalking 日志接收与降级

- 依赖：`OBS-01`、`LOG-01`、`LOG-02`。
- 范围：接入兼容 Reporter、OAP Log Receiver、LAL、独立遥测存储和 UI 查询；建立上报失败、丢弃和解析错误指标。
- 验收：日志可按 `traceId/swTraceId/domainTaskId/aiCallId` 查询；OAP 断开时业务继续、队列有界、本地日志可读且产生失败/丢弃指标。


### 阶段 3：SkyWalking Trace 与长耗时 AI 关联

#### [ ] TRACE-01 Java Agent 自动埋点接入

- 依赖：`OBS-01`、`OBS-02`。
- 范围：为 13 个当前服务接入 Agent，验证 Gateway、HTTP、Feign、JDBC、RocketMQ 和服务拓扑；建立环境、版本、实例资源标签与采样配置。
- 验收：典型同步请求可以从 Gateway 追踪到下游和数据库；业务 `traceId` 与 SkyWalking Trace 可双向定位。

#### [ ] TRACE-02 异步边界与 Worker attempt Span

- 依赖：`TRACE-01`。
- 范围：为 Outbox Relay、Inbox 短事务、领域任务创建、租约 Worker attempt、AI 调度/调用和派生事件补充必要的自定义 Span；不记录业务正文。
- 验收：评审、建议和评价的每个物理 attempt 都是有界 Trace；租约接管产生新 Trace/attempt，同时仍能通过持久化业务标识串联完整生命周期。

#### [ ] TRACE-03 Trace、日志、指标联合验收

- 依赖：`MET-04`、`LOG-03`、`TRACE-02`。
- 范围：打通 Grafana 告警、SkyWalking Trace/日志、业务关联查询和 AI Call 事实。
- 验收：从一条积压或 UNKNOWN 告警能够定位执行阶段、脱敏错误、领域任务、attempt 和 AI Call；采样后仍可依靠业务 `traceId` 解释结果。


### 阶段 4：中央操作审计基础设施

#### [ ] AUD-01 审计契约、操作目录与专用 MQ 资源

- 依赖：`OBS-02`。
- 范围：定义公共审计信封、`REQUESTED/PENDING` 与 `COMPLETED` 结果、字段白名单、风险等级和 P0 操作目录；创建 `leetmodel-operation-audit-v1`、`cg-audit-archive-v1` 及 ACL/重试/DLQ 配置。
- 验收：`auditEventId` 等于消息 `eventId`；消息小于 64 KiB；未知 schema/操作编码 fail-fast；不携带敏感正文或泛化实体快照。

#### [ ] AUD-02 audit-service 骨架与 lm_audit

- 依赖：`AUD-01`。
- 范围：创建目标 Maven 服务、服务配置、Flyway、Inbox 和只追加 `operation_audit_event`；限制应用数据库账号的更新/删除能力。
- 验收：服务独占 `lm_audit`，不直连业务数据库；归档唯一约束和 `operationId` 时间线、操作者、目标、操作结果索引通过集成测试。

#### [ ] AUD-03 审计消费、幂等归档与完整性监测

- 依赖：`AUD-02`。
- 范围：实现契约校验、Inbox 去重、只追加归档、非法 schema/DLQ、归档延迟和未完整操作检测。
- 验收：重复、乱序和重放不会复制审计；只有 `REQUESTED/PENDING` 且超过 deadline 的操作产生告警，不自动重做外部副作用。

#### [ ] AUD-04 中央查询、权限与保留治理

- 依赖：`AUD-03`。
- 范围：提供受信内部只读查询，支持时间、服务、操作、风险、操作者、目标、结果、`operationId`、`traceId` 和 `swTraceId`；落实在线保留、归档、备份和导出权限。
- 验收：普通管理员不能越权读取或导出；查询源不可用时显式失败；导出、保留和权限变更自身可审计。


### 阶段 5：领域审计生产者与管理端

#### [ ] AUD-05 用户、安全与 RBAC 审计

- 依赖：`AUD-01`、`AUD-03`。
- 范围：覆盖登录成功/失败、密码修改、账号状态、用户角色和角色权限；Gateway 清理伪造内部头，user-service 重新授权并生成语义事件。
- 验收：角色变更与 `COMPLETED/SUCCEEDED` 审计 Outbox 同事务提交或回滚；拒绝事件使用独立短事务且不记录密码、验证码或 Token。

#### [ ] AUD-06 内容、提交与领域治理审计

- 依赖：`AUD-01`、`AUD-03`。
- 范围：覆盖题目/赛事/附件管理、最终提交和其他已确认高风险领域操作；每个 operationCode 独立定义前后差异白名单。
- 验收：业务所有者计算差异并写本地审计 Outbox；admin-service 不生成成功审计、不复制领域规则。

#### [ ] AUD-07 AI、消息与派生数据治理审计

- 依赖：`AUD-01`、`AUD-03`。
- 范围：覆盖 AI 队列/评价任务治理、生产版本切换、Consumer 暂停恢复、Outbox/DLQ 重放和排行重建；保留现有 AI 调用及领域审计的原语义。
- 验收：外部副作用均追加 `REQUESTED/PENDING` 与 `COMPLETED/SUCCEEDED|FAILED`；AI 调用计量不复制成人工审计；高风险原因结构化保存。

#### [ ] AUD-08 管理端统一操作审计页

- 依赖：`AUD-04`、`AUD-05`、`AUD-06`、`AUD-07`。
- 范围：admin-service 代理受权限控制的查询，前端提供筛选列表、操作阶段、目标时间线、白名单差异以及 Trace 复制/跳转。
- 验收：admin-service 无审计数据库和副本；部分来源或中央查询不可用时显式标记；页面不提供越权修改、删除或默认批量导出。


### 阶段 6：容量保护、故障演练与最终验收

#### [ ] SAFE-01 遥测和审计故障保护

- 依赖：`TRACE-03`、`AUD-08`。
- 范围：实现日志有界队列、遥测空洞表达、审计 Outbox 水位和高风险操作 fail-closed；普通业务与高风险治理采用不同降级策略。
- 验收：OAP、Prometheus、Broker 或 audit-service 分别中断时不会耗尽业务线程/内存；高风险审计长时间无法可靠归档时拒绝新的高风险操作。

#### [ ] SAFE-02 全链路故障演练

- 依赖：`SAFE-01`。
- 范围：演练 OAP/Prometheus/audit-service 中断、日志队列溢出、审计重复/乱序/DLQ、MQ 积压、租约接管、AI UNKNOWN 和遥测存储故障。
- 验收：每个场景都有可观察信号、Runbook、受审计恢复动作和恢复判据；故障不会被零值、空集合或成功状态掩盖。

#### [ ] SAFE-03 最终门禁与文档收敛

- 依赖：`SAFE-02`。
- 范围：运行全部受影响模块测试、真实依赖协议门禁和安全负面测试；核对代码、配置、Flyway、看板、告警、Runbook 与设计文档。
- 验收：核心请求能够完成“指标发现 → Trace/日志定位 → 业务事实/操作审计确认 → 管理端受控恢复 → 指标恢复”的闭环；实现差异已同步正式文档，TODO 中已完成任务卡按约定删除。

## 条件任务

### [ ] REST `/v1`、`/v2` 版本化

- 触发：实际出现无法兼容演进的外部 API 变更，并且能够列出真实调用方与迁移时点。
- 当前：Gateway、Controller、Feign、OpenAPI 和活跃前端调用已完成触发盘点，没有真实外部不兼容需求，因此暂不启动。
- 边界：不使用 REST 版本代替 `workflowVersion`；触发后才定义双路由、弃用观测和下线周期。
- 证据：[REST API 版本化触发评估](docs/project/02-架构设计/REST%20API版本化触发评估.md)。

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
