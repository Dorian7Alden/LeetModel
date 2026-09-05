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

后端系统全部微服务（包括 common 基础层及 13 个业务微服务）的代码注释契约与排版规范化已全部完成并合入 dev。

## 当前任务

### 阶段：AI评审V3（DEEP_EVIDENCE_REVIEW_V3）功能实现与端到端交付

分支：`phase/review-workflow-opt`
输入基线：`PAPER_DOCUMENT_V2`（第二代高保真结构化解析产物）
实现准则：固定工作流骨架、动态提示词安全渲染、双阶段解耦评审、并行消费隔离、五维确定性汇聚、全链路防御解析与算术断言。

- [ ] 实现任务 4：阶段一审查算子、阶段二任务规划算子与 Worker 执行算子
  - 目标：完成各阶段独立模型推理算子。
  - 范围：
    * 实现 `Phase1StructuralReviewOperator`（阶段一静态规范模型调用与解析校验）；
    * 实现 `TaskPlannerOperator`（基于小问与 SectionIndex 的任务规划算子）；
    * 实现 `SubTaskEvaluationWorker`（分小问模型推演、摘要核验与灵敏度专项推理，支持局部容错与降级）。

- [ ] 实现任务 5：隔离线程池配置与终态维度合成器（Reducer）
  - 目标：完成子任务并行调度与纯 Java 内存无漂移算术汇聚。
  - 范围：
    * 配置专有隔离线程池 `reviewSubTaskExecutor`（容量规划与超时控制）；
    * 实现 `DeepEvidenceReviewV3Reducer`（将阶段一与阶段二结果映射至五大标准化终态维度，执行 `totalScore == sum(dimensionScore)` 算术断言与 Findings 去重归并）。

- [ ] 实现任务 6：工作流实现（DeepEvidenceReviewV3Workflow）、结果持久化与端到端集成测试验收
  - 目标：完成工作流闭环组装、结果持久化并跑通全流程测试。
  - 范围：
    * 实现 `DeepEvidenceReviewV3Workflow` 并注册进 `ReviewWorkflowRegistry`；
    * 在 `ReviewResultPersistenceService` 中支持 V3 结果及中间态快照持久化；
    * 适配 `ReviewService` 支持 V3 结果展示；
    * 编写完整的单元测试与端到端集成测试用例，执行 Maven 构建并验证全部测试通过。


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
