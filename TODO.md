# LeetModel TODO

> 本文件只保存当前任务、候选任务和满足条件后才启动的事项。已完成任务和阶段过程不在此保留；已经确认的长期边界与后续触发条件保留在本文件，具体设计和实现结论以 `docs/` 为准。

## 使用约定

- `[ ]` 表示待开始，`[~]` 表示进行中，`[!]` 表示被外部条件阻塞。
- 每次开始前阅读根 `AGENTS.md`、`README.md`、本文件、相关规范、目标模块 README 和任务指向的设计文档。
- 修改公共契约前检查全部生产者和消费者；数据库变更只新增 Flyway 迁移。
- 任务完成至少同步受影响的正式文档并运行目标模块测试；公共模块变化时验证直接消费者。
- 不在配置、日志、测试夹具或文档中保存真实密钥、Relay Token、Prompt、回答正文、知识片段或论文内容。
- new-api Relay Token 由 `ai-gateway-service` 的运行环境提供；具体脱敏和模型复核规则见 [AI 网关测试与验收](docs/project/03-微服务设计/ai-gateway-service/22-测试与验收.md)。
- `cli-proxy-api` 是长期运行且不属于本项目的本地 Docker 服务，固定占用宿主机 `8085`；不得停止、重启、改端口或修改其配置。LeetModel 本地 submission-service 使用 `8092`，启动与验收必须避开该容器。
- 可以自主创建本地阶段分支、执行任务卡原子 commit 和阶段 merge；未经用户明确授权，不执行 push、force push、rebase、破坏性 reset、改写历史、远端分支操作或删除用户文件。

## 当前状态

当前已切入阶段分支 `phase/s9-ai-evaluation-refinement`，推进 S9 AI 评测轻量化与实用化改造。以实用主义为导向，聚焦硬核指标（成功率、响应耗时、Token/费用、评分稳定性方差），做减法消除使用门槛，并打通固定工作流缺失能力。

## 当前任务

### [ ] 任务 3：打通 AI 建议（SUGGESTION）隔离实验与评测 Runner（补齐固定工作流观测闭环）

**目标**：
打通固定工作流中 AI 建议（SUGGESTION）的离线评测链路：在 `ai-suggestion-service` 增加类似 `ai-review-service` 的隔离实验接口（不落生产任务库，接收 `evaluationTaskId` 并透传至网关），在 `ai-evaluation-service` 实现 `SuggestionEvaluationRunner`，并支持样本 Payload 校验、功能目录发现与运行事实提取。

**入口**：
- `ai-suggestion-service`: `InternalSuggestionController.java`、`SuggestionService.java`
- `ai-evaluation-service`: `SuggestionEvaluationRunner.java`、`EvaluationRunnerRegistry.java`、`EvaluationSamplePayloadService.java`
- `common-api`: `SuggestionFeignClient.java`

**主流程**：
1. 在 `common-api` 中扩展/定义 `SuggestionFeignClient`，包含 `getFeatureDefinition` 与 `runExperiment` 接口。
2. 在 `ai-suggestion-service` 中实现 `POST /internal/suggestions/experiments`：
   - 接收 `AiExperimentRequestDTO`；
   - 解析入参中的 `submissionId`（或依据快照），以无副作用的 transient 模式调用现有的 `SuggestionV1Workflow` 或 `GroundedSuggestionV2Workflow`；
   - 将 `evaluationTaskId`、`slotKey` 与 P3 优先级透传至 AI Gateway；
   - 返回 `AiExperimentResultDTO`，包含结构化建议摘要（`outputJson`）、耗时与 `aiCallId`，不写入 `suggestion_task` 业务表。
3. 在 `ai-evaluation-service` 中：
   - `EvaluationSamplePayloadService` 支持 `SUGGESTION` 功能，样本 schema 为 `SUGGESTION_SUBMISSION_V1`（引用 `submissionId`，与 REVIEW 一致保持轻量）；
   - 编写 `SuggestionEvaluationRunner`，实现 `EvaluationExperimentRunner` 契约，注册进 `EvaluationRunnerRegistry`；
   - `EvaluationMetricRegistry` 与指标计算器支持 `SUGGESTION` 功能的运行指标提取。
4. 编写对应的单元测试与 Mock 实验测试，确保全套流程可验证。

**完成标准**：
1. `ai-suggestion-service` 隔离接口在不落库的前提下能够运行建议工作流并返回标准结果。
2. `ai-evaluation-service` 可以基于 `SUGGESTION` 功能创建测试集与评测任务。
3. `SuggestionEvaluationRunnerTest` 通过，验证隔离调用、身份断言与结果解析无误。
4. `ai-suggestion-service` 和 `ai-evaluation-service` 模块测试全部通过。

**修改范围**：
- `LeetModel-backend/common/common-api/src/main/java/com/leetmodel/common/api/feign/SuggestionFeignClient.java`
- `LeetModel-backend/ai-suggestion-service/src/main/java/com/leetmodel/suggestion/controller/InternalSuggestionController.java`
- `LeetModel-backend/ai-suggestion-service/src/main/java/com/leetmodel/suggestion/service/SuggestionService.java`
- `LeetModel-backend/ai-evaluation-service/src/main/java/com/leetmodel/evaluation/service/EvaluationSamplePayloadService.java`
- `LeetModel-backend/ai-evaluation-service/src/main/java/com/leetmodel/evaluation/runner/SuggestionEvaluationRunner.java`
- `LeetModel-backend/ai-evaluation-service/src/main/java/com/leetmodel/evaluation/service/EvaluationMetricRegistry.java`
- 对应测试类

**非目标**：
- 不修改生产建议提交接口行为与数据库结构。

---

## 阶段后续任务规划（S9-Evaluation-Refinement）

- [x] 任务 1：评测任务解除权重方案强制绑定（支持纯基准观测模式）
- [x] 任务 2：精简 AI 客服评测样本契约与伪指标（移除人工标注假定，收敛至可用性、耗时与Token成本）
- [ ] 任务 4：端到端评测闭环验证与文档同步（验证无权重方案创建、建议与客服评测主链，同步更新相关设计文档）

**目标**：
解除创建评测任务时对 `weightSchemeId` 的强制校验，允许在未指定权重方案时正常创建并运行评测任务，完整收集和落库响应时间、成功率、Token 消耗、实际扣费与方差事实，仅将版本选择指数标记为置空/未计算，降低评测使用的初始化门槛。

**入口**：
- POST `/internal/evaluations/tasks`（创建评测任务接口）
- `EvaluationTaskCreateDTO.java`
- `EvaluationService.createTask` 与 `refreshTask`

**主流程**：
1. 将 `EvaluationTaskCreateDTO.weightSchemeId` 校验注解 `@NotNull` 移除，允许传 `null`。
2. `EvaluationService.createTask` 中移除强制校验异常，若 `weightSchemeId` 为空，跳过方案快照与绑定逻辑，任务主表 `weight_scheme_id` 和 `weight_scheme_version` 置空。
3. 槽位执行逻辑不受影响，依然按原逻辑正常执行隔离实验并记录各槽位运行事实。
4. 槽位全部完成后，`refreshTask` 照常拉取网关真实 Token/费用，计算方差、耗时和成功率等原始指标；若任务未绑定权重方案，跳过版本选择指数合成，直接完成任务并持久化。
5. 跨服务 DTO 转换与接口响应保证在无权重方案时兼容返回。

**完成标准**：
1. 调用 POST `/internal/evaluations/tasks` 不传 `weightSchemeId` 能成功创建评测任务，状态流转至 `WAITING`。
2. 槽位执行完毕后，任务正常进入 `COMPLETED` 终态。
3. 任务详情中能完整查看原始指标（`rawMetrics`）：包含成功率、平均耗时、Token 用量、费用以及评审打分的方差/极差。
4. 任务的 `versionSelectionIndex` 正确置空，无空指针异常或未处理异常。
5. 补充或调整对应单测，`mvn -pl ai-evaluation-service test` 全部通过。

**修改范围**：
- `LeetModel-backend/common/common-api/src/main/java/com/leetmodel/common/api/dto/EvaluationTaskCreateDTO.java`
- `LeetModel-backend/ai-evaluation-service/src/main/java/com/leetmodel/evaluation/service/EvaluationService.java`
- `LeetModel-backend/ai-evaluation-service/src/main/java/com/leetmodel/evaluation/service/EvaluationScoreResultService.java`
- `LeetModel-backend/ai-evaluation-service/src/test/java/com/leetmodel/evaluation/service/EvaluationServiceTest.java`

**非目标**：
- 本卡不修改客服指标逻辑（由任务 2 处理）。
- 本卡不实现 AI 建议隔离实验（由任务 3 处理）。
- 本卡不修改 Flyway 数据库表结构（主表相关列已允许为 NULL）。

---

## 阶段后续任务规划（S9-Evaluation-Refinement）

- [ ] 任务 2：精简 AI 客服评测样本契约与伪指标（移除强依赖人工标准要点/来源覆盖的假定，收敛至接口可用性、响应延迟与实际 Token/成本）
- [ ] 任务 3：打通 AI 建议（SUGGESTION）隔离实验与评测 Runner（`ai-suggestion-service` 增加隔离实验接口，`ai-evaluation-service` 新增 `SuggestionEvaluationRunner`，补齐固定工作流观测闭环）
- [ ] 任务 4：端到端评测闭环验证与文档同步（验证无权重方案创建、建议与客服评测主链，同步更新相关设计文档）


## 待梳理服务清单（按推荐顺序）

全平台微服务（common、user、team、problem、submission、ranking、gateway、admin、audit、knowledge-retrieval、ai-gateway、ai-suggestion、ai-review、ai-assistant、ai-evaluation）代码注释已全量对齐项目级工程规范。

## 已确认的系统边界

- SkyWalking 是唯一 Trace/APM 实现；Prometheus 是指标与告警权威来源；运行日志不能代替操作审计。
- 业务 `traceId` 必须保留并可在 Trace 采样或遥测后端不可用时回退到结构化日志和业务事实。
- audit-service 只负责中央不可变归档与受信只读查询，不拥有用户、题目、提交、AI 任务、消息任务或生产配置等领域规则。
- 管理端通过受权限保护的服务接口执行取消、暂停、恢复、重放和回滚；Grafana、Prometheus、SkyWalking UI 只读观察。
- 遥测后端故障不得阻塞普通业务主链；审计 Outbox 保留并重试，达到严重水位时高风险治理操作 fail-closed。
- 指标、日志、Trace 和审计均遵守低基数、最小数据和脱敏边界，不保存用户/队伍/提交/任务/调用标识作为 Prometheus 标签。

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
