# LeetModel TODO

> 本文件只保存当前及后续可执行任务、依赖与简短的已完成阶段摘要。已经结束阶段的关键决策、边界和验收依据统一归档到 [AI 已完成阶段归档](docs/project/02-架构设计/AI已完成阶段归档.md) 及其链接的原子设计文档，避免 TODO 随历史任务持续膨胀。
>
> 当前已启用托管模式。Agent 按依赖一次只领取一个编号任务卡；同一阶段的多个任务卡在一条阶段分支内串行完成并形成原子提交，整个阶段验收后才非快进合回 `dev` 并清理已合并的本地阶段分支。

## 使用约定

- [ ] 待开始；[~] 进行中；[x] 已完成并验收；[!] 被外部条件阻塞。
- 每次开始前阅读根 `CONTEXT.md`、`README.md`、本文件、相关规范、目标模块 README 和任务卡指向的设计文档。
- 修改公共契约前检查全部生产者和消费者；数据库变更只新增 Flyway 迁移。
- 任务完成至少同步受影响的正式文档并运行目标模块测试；公共模块变化时验证直接消费者。
- 不在配置、日志、测试夹具或文档中保存真实密钥、Relay Token、Prompt、回答正文、知识片段或论文内容。
- new-api Relay Token 由 `ai-gateway-service` 的运行环境提供；具体脱敏和模型复核规则见 [AI 网关测试与验收](docs/project/03-微服务设计/ai-gateway-service/22-测试与验收.md)。
- `cli-proxy-api` 是长期运行且不属于本项目的本地 Docker 服务，固定占用宿主机 `8085`；不得停止、重启、改端口或修改其配置。LeetModel 本地 submission-service 使用 `8092`，启动与验收必须避开该容器。
- 可以自主创建本地阶段分支、执行任务卡原子 commit 和阶段 merge；未经用户明确授权，不执行 push、force push、rebase、破坏性 reset、改写历史、远端分支操作或删除用户文件。

## 已完成阶段摘要

| 阶段 | 完成摘要 | 长期记录 |
|------|----------|----------|
| D | 正式设计校准完成，统一 new-api/AI 网关职责、RAG V1/V2、评价术语和五种版本标识。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#正式设计校准) |
| S0 | 保留 LangChain4j 0.34.0，确认项目内 Embedding 适配与 Elasticsearch 技术基线。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s0-langchain4j-兼容性) |
| S1 | Chat 唯一生产链切换至 new-api，删除供应商官方直连和隐式运行时回退。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s1-new-api-chat-主链) |
| S2 | 统一业务调用上下文、Token、费用、耗时、失败与调用关联事实。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s2-调用计量与成本) |
| S3 | Embedding 统一经过 AI 网关，RAG_V1 锁定 qwen3.7-text-embedding 与 1024 维。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s3-embedding-统一调用) |
| S4 | 客服向量 RAG V1 完成受控知识选择、稳定索引、召回、审计、降级和回滚。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s4-客服-rag-v1) |
| S5 | AI 网关完成单实例持久化公平调度、P0 保留、幂等、租约、恢复、UNKNOWN 与运维入口。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s5-ai-网关优先级调度) |
| S6 | REVIEW/ASSISTANT 完成功能目录、不可变模型配置快照与无正式业务副作用的通用隔离实验。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s6-ai-功能与版本契约) |
| S7 | REVIEW/ASSISTANT 通用评价平台完成版本化数据集、隔离运行、可信指标、控制与同口径比较。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s7-通用评价平台) |
| S8 | 评价权重、不可变选择指数、重算、管理代理、页面与真实端到端闭环完成。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s8-权重与管理端闭环) |
| S9 | RAG V2 完成受控轻量目录、两阶段选文、固定对比实验和实施门槛设计，未写运行时代码。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s9-rag-v2-受控目录设计) |
| S10 | AI 客服完成生产配置所有权、条件激活、运行快照、审计、管理端二次确认和真实回滚；REST 全局版本化触发条件未成立。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s10-生产工作流版本治理) |
| S11 | AI 客服完成公共工具协议、题目查询/推荐、知识讲解、受控编排、调用审计、工具版生产快照和真实激活/回滚。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s11-ai-客服受控工具调用) |
| S12 | 完成强制版本化 PDF 解析、证据化评审 V2、独立知识检索和有依据建议 V2；支持逐论文版本手动多次生成并完整保留 V1。 | [归档](docs/project/02-架构设计/AI已完成阶段归档.md#s12-有依据的-ai-论文建议闭环) |
| I1 | Nacos 2.3.2 纳入 Docker Compose，使用单机 Derby 命名卷持久化，`start-mvp.sh` 不再依赖宿主机安装目录。 | [运行说明](README.md#启动-nacos) |
| C1 | 完成 HTTP、Caffeine、Redis 三级缓存策略设计，首期锁定公开题库和当前排行，明确多级回填、版本化 Cache Aside、Outbox 可靠失效、Pub/Sub 与版本对账、故障降级和可量化验收门槛，尚未写运行时代码。 | [缓存策略](docs/project/02-架构设计/缓存策略.md) |
| C2 | 公开题库与当前排行完成 HTTP、Caffeine、独立业务 Redis 三级缓存，具备 Outbox 可靠失效、Pub/Sub、五秒对账、空值与 Redis 故障降级。 | [缓存策略](docs/project/02-架构设计/缓存策略.md#实施与验收结果) |
| MQ0 | 完成 RocketMQ 多级任务与事件设计，确认 Outbox/Inbox、领域任务租约、在线与批任务隔离、DLQ、配置、迁移和故障验收边界，尚未写运行时代码。 | [RocketMQ 消息队列](docs/project/02-架构设计/RocketMQ消息队列.md) |
| MQ1 | 固定 Broker Docker 5.5.0 与 RocketMQ Spring 2.3.3，完成显式 NORMAL 资源、持久化本地设施、消息信封、事务 Outbox/Inbox、租约 Relay、指标与真实协议验证。 | [common-messaging](docs/project/03-微服务设计/common/common-messaging/README.md) |
| MQ2 | 提交到评审改为事务 Outbox、RocketMQ、Inbox 与领域任务链路，完成有界 Worker、租约恢复、稳定 AI 幂等键、UNKNOWN 保护和 Feign Relay 回退。 | [RocketMQ 消息队列](docs/project/02-架构设计/RocketMQ消息队列.md#提交触发评审) |
| MQ3 | 最终提交与评审完成事件可靠驱动按题目合并排行重建，完成 revision 合并、租约 fencing、失败保旧与权威事实指纹对账。 | [RocketMQ 消息队列](docs/project/02-架构设计/RocketMQ消息队列.md#排行重建) |
| MQ4 | 建议任务改为事务 Outbox、Inbox 与 RocketMQ 可靠唤醒，完成单并发有界 Worker、租约 fencing、稳定 AI 幂等键、UNKNOWN 保护、积压拒绝与低频对账。 | [RocketMQ 消息队列](docs/project/02-架构设计/RocketMQ消息队列.md#手动建议任务) |
| MQ5 | 后台评价槽位改为独立 Topic、Inbox 与事务 Outbox 可靠唤醒，完成单并发租约 Worker、P0/P1 水位保护、fencing、UNKNOWN 与低频对账。 | [RocketMQ 消息队列](docs/project/02-架构设计/RocketMQ消息队列.md#后台评价) |

## 当前执行路线

S0 至 S12、M1、U1、I1、C1、C2、MQ0 以及 MQ1 至 MQ6 已完成。RocketMQ 可靠异步链路的设计、实现与运维验收已经闭环。

### [x] MQ1 RocketMQ 基础设施与公共契约

- 目标：建立可复现的 RocketMQ 本地基础设施和不含业务语义的 `common-messaging` 公共能力。
- 依赖：MQ0 设计已确认。
- 主流程：固定并真实验证 Broker Docker 5.5.0 与 RocketMQ Spring 2.3.3 基线；Docker Compose 启动 NameServer、Broker 与可选 Dashboard；版本化脚本显式创建 NORMAL Topic 和消费组；公共模块提供 `MessageEnvelopeV1`、64 KiB 校验、Outbox Relay、Inbox 幂等、配置校验、低基数指标和测试替身。
- 完成标准：JDK 17、Spring Boot 3 下真实发送、消费、重复投递、Broker 重启和数据卷恢复通过；Topic 类型、namespace、ACL 预留、健康检查和停机说明完整；不使用自动创建伪装资源初始化成功。
- 修改范围：父 POM、`common-messaging`、Docker Compose、基础设施脚本、启动停止脚本、公共测试和运行文档。
- 非目标：不迁移任何业务生产者或消费者，不启用事务、延迟和顺序消息，不建设生产多副本集群。
- 完成摘要：Docker Compose 使用固定 5.5.0 镜像运行 NameServer、单 Broker 和可选 Dashboard，关闭 Topic/消费组自动创建并通过脚本显式建立 5 个 NORMAL Topic、5 个最大重试 5 次的消费组；`common-messaging` 提供 UUID/ULID 与 64 KiB 契约校验、同库 Outbox、逐条续租 Relay、分级退避与 BLOCKED、同事务 Inbox、低基数指标、健康检查和内存测试发布器。
- 验收：公共模块 19 项常规测试通过，其中 1 项外部协议测试默认按门禁跳过；后端全量 544 项测试中 531 项通过、13 项按既有门禁跳过、无失败。打开门禁后 RocketMQ Spring 2.3.3 真实发布、消费、同 eventId 重复投递一次执行和一次失败重投通过。资源初始化、消费组策略、Dashboard 启动、Broker 重启、offset 与按 Key 数据卷恢复均真实验证；5.5.1 无 Docker Hub 标签，因此可复现镜像基线校准为 5.5.0。

### [x] MQ2 提交到评审可靠异步链路

- 目标：把提交成功后的评审触发从请求线程 Feign 改为 Outbox、RocketMQ、Inbox 与 review_task 的可靠链路，同时补齐长评审崩溃恢复。
- 依赖：MQ1。
- 主流程：submission 事务写 `REVIEW_TASK_READY` Outbox 并返回评审派发状态；ai-review-service 短事务消费并幂等创建任务；Worker 使用有界并发、租约、heartbeat、attempt 和稳定 AI 幂等键执行；实现 `MQ_PRIMARY` 与受控 `FEIGN_RELAY` 回退。
- 完成标准：事务回滚、提交后宕机、ACK 丢失、重复消费、消费者崩溃、Worker 崩溃、Broker 停机恢复和 AI UNKNOWN 均有自动化或真实故障证据；同一 submission 与 workflow 只产生一个评审任务和一份结果。
- 修改范围：submission-service、ai-review-service、common-api、Flyway、前端评审排队状态、测试和相关文档。
- 非目标：不改变评审工作流语义，不让 MQ 消费线程执行 PDF 解析或 AI 调用，不删除任务表。
- 完成摘要：提交版本、上传关联和 `REVIEW_TASK_READY` Outbox 在同一本地事务提交，默认请求线程不调用评审 Feign；真实消费者以 Inbox 和领域唯一键收敛重复消息。评审 Worker 使用并发 2 的有界领取、租约、逐任务 token heartbeat、fencing completion、分级依赖重试、稳定 AI 幂等键和 UNKNOWN 终态；支持读取同一 Outbox 的 `FEIGN_RELAY` 受控回退，并为历史缺失 Outbox 的既有提交提供幂等补偿。
- 验收：后端全量 560 项测试中 546 项通过、14 项外部门禁跳过、零失败；MQ2 目标模块 submission-service 24 项与 ai-review-service 36 项测试零失败。打开门禁后，真实 RocketMQ 重复投递同一 eventId 仅执行一次 Inbox 业务处理；事务提交/回滚、Outbox 退避、消费者回滚、租约丢失与过期恢复、fencing、AI UNKNOWN 不重试和 Feign Relay 均有自动化或真实协议证据。submission V1→V4、review V1→V5 在真实 MySQL 完成迁移并验证过期 RUNNING 任务被新 owner 领取；前端生产构建通过。

### [x] MQ3 评审与最终提交驱动排行

- 目标：让评审完成和最终提交变化可靠触发排行，并在事件洪峰中按题目合并全量重建。
- 依赖：MQ2。
- 主流程：review 结果事务写 `REVIEW_COMPLETED` Outbox，submission 最终锁定事务写 `FINAL_SUBMISSION_CHANGED` Outbox；ranking 两个消费组写 Inbox 并 upsert 单题 `ranking_rebuild_task`；Worker 用 requested/completed revision 合并重复事件，现有排行重建事务继续写缓存失效 Outbox。
- 完成标准：两个事件到达顺序任意、重复、并发和重建中再次到达均收敛到正确当前排行；依赖失败不覆盖旧批次；周期对账能修复漏事件；同题洪峰不会形成同数量的全量重建。
- 修改范围：submission-service、ai-review-service、ranking-service、Flyway、测试、管理查询和文档。
- 非目标：消息不携带或复制分数，不用 MQ 替代排行快照与缓存失效机制。
- 完成摘要：最终提交锁和评审完成分别与 `FINAL_SUBMISSION_CHANGED`、`REVIEW_COMPLETED` Outbox 同事务提交；ranking-service 两个独立 Inbox 消费组把事件收敛到每题唯一 revision 任务。并发 1 Worker 使用 300 秒租约、20 秒逐任务 heartbeat 和 fencing 写入，失败事务保留旧排行，运行中新增事件只触发一次补跑，每小时权威事实 SHA-256 指纹对账修复漏事件。
- 验收：后端全量 574 项测试中 559 项通过、15 项外部门禁跳过、零失败；目标模块 submission-service 26 项、ai-review-service 38 项、ranking-service 19 项测试零失败。打开门禁后真实 RocketMQ 以任意顺序重复投递两个事件，两个消费组各只执行一次业务请求；submission V1→V4、review V1→V6、ranking V1→V4 在真实 MySQL 完成迁移，并验证 revision 合并、过期租约接管与 recovery 计数；ranking-service 使用新库真实启动并完成 Flyway V1→V4。

### [x] MQ4 建议任务唤醒与租约恢复

- 目标：把建议任务从高频数据库轮询迁移为“任务事实加 Outbox、MQ 唤醒、租约 Worker”，保持多次生成和历史结果语义。
- 依赖：MQ2。
- 主流程：建议任务创建与 `SUGGESTION_TASK_READY` 同事务提交；Inbox 幂等唤醒；本地 Worker 使用单实例初始并发 1、租约、heartbeat、attempt 和稳定 AI 幂等键；低频 reconciliation 只修复到期未完成任务。
- 完成标准：重复点击继续由 `clientRequestId` 去重，用户重试形成新 attempt；十分钟合法长任务不被误恢复；进程与 Broker 崩溃后任务收敛；严重在线积压时新建议返回稳定繁忙错误。
- 修改范围：ai-suggestion-service、Flyway、前端状态与错误展示、测试和文档。
- 非目标：不改变 `GROUNDED_SUGGESTION_V2` 依据链，不覆盖历史报告，不复制检索与评审事实。
- 完成摘要：建议任务创建和 `SUGGESTION_TASK_READY` Outbox 在同一事务提交，Inbox 只推进一次领域唤醒并对重复消息再次发出有界本地信号。Worker 固定单并发，使用 120 秒租约、20 秒 heartbeat、逐任务 fencing token 与稳定 AI 幂等键；只对明确的依赖不可用形成最多 3 个 attempt，证据等待不增加 attempt，AI 结果未知进入 `UNKNOWN`。30 秒 reconciliation 只修复到期等待和过期租约，严重积压返回 `40807`。
- 验收：后端全量 585 项测试中 569 项通过、16 项外部门禁跳过、零失败；ai-suggestion-service 34 项中 33 项通过、1 项真实 Broker 门禁默认跳过。打开门禁后 RocketMQ 5.5.0 与 Client 5.3.1 下同一 eventId 重复投递只执行一次 Inbox 领域动作并发出两次可恢复唤醒；任务与 Outbox 提交/回滚、租约领取与丢失、heartbeat、稳定 AI 键、UNKNOWN、依赖重试和积压拒绝均有自动化证据。真实 MySQL 8 完成 Flyway V1→V3，服务以真实消息消费者完整启动；前端生产构建通过。

### [x] MQ5 后台评价隔离

- 目标：用独立 Topic、消费组和低并发 Worker 承载评价槽位，证明批任务不会挤占正式评审、建议和客服。
- 依赖：MQ1、MQ4。
- 主流程：评价运行槽位事实与 `EVALUATION_SLOT_READY` Outbox 同事务提交；消费者唤醒既有槽位；在线核心队列超过警告水位时暂停新领取，恢复后继续；AI 调用仍按可信来源映射为 P3。
- 完成标准：暂停、恢复、取消、重复消息、进程重启和大量积压均保持统计口径与 attempt 历史；客服 P0 和正式 P1 的现有容量保护回归通过。
- 修改范围：ai-evaluation-service、admin-service 管理控制、Flyway、测试和文档。
- 非目标：不修改评价指标、权重或版本选择指数，不用 Broker 消费失败实现限速。
- 完成摘要：评价任务创建、失败项重试和恢复与 `EVALUATION_SLOT_READY` Outbox 同事务提交；独立 Inbox 消费组只推进一次领域唤醒，并在重复投递时再次发出有界本地信号。Worker 固定单并发，使用 120 秒租约、20 秒 heartbeat 和逐 attempt fencing token；30 秒 reconciliation 只补偿到期等待槽位。领取前读取 AI 网关 P0/P1 排队数量和最老等待时间，达到水位或水位查询失败时 fail-closed 暂停，恢复后自动继续；评价原子调用仍由可信映射保持 P3。
- 验收：后端全量 596 项测试中 579 项通过、17 项外部门禁跳过、零失败；ai-evaluation-service 96 项中 95 项通过、1 项真实 Broker 门禁默认跳过。打开门禁后 RocketMQ 5.5.0 下同一 eventId 重复投递只执行一次 Inbox 领域动作并发出两次可恢复唤醒；任务/Outbox 提交回滚、压力暂停、单并发领取、租约 heartbeat、fencing、暂停/恢复/取消、attempt 历史和 UNKNOWN 均有自动化证据。真实 MySQL 8 完成 Flyway V1→V9，服务以真实 RocketMQ 消费者完整启动。

### [x] MQ6 运维治理、故障演练与旧链清理

- 目标：完成积压、Outbox、Inbox、领域任务、DLQ 和重放的统一运维闭环，并在全链验收后删除旧主路径。
- 依赖：MQ2 至 MQ5。
- 主流程：增加 traceId 至 aiCallId 关联查询、消费暂停、单条和受控批量 DLQ 重放、Outbox 补发、积压水位告警与管理页面；执行应用 kill、Broker 重启、网络中断、数据库短故障、重复与乱序消息等故障演练。
- 完成标准：设计文档中的故障矩阵全部有证据；DLQ 不自动回灌；`MQ_PRIMARY` 可稳定运行并可回退到 `FEIGN_RELAY`；确认无调用方后删除用户请求线程旧 Feign 触发和无界轮询，后端全量测试、真实服务启动与前端关键路径通过。
- 修改范围：admin-service、相关业务服务、运维脚本、Dashboard/指标接入、端到端测试、README 与归档。
- 非目标：不实现生产多副本 RocketMQ 集群，不执行远端部署或 push。
- 完成摘要：五个消息所有者服务通过统一内网契约提供脱敏 Outbox、Inbox、领域任务、真实 consumer 和 Broker DLQ 状态，admin-service 以部分成功语义聚合并提供积压告警、traceId 链路查询、真实消费暂停/恢复、原 eventId Outbox 补发，以及先定位死信再按源服务补发的单条/最多 20 条人工 DLQ 重放。DLQ 不自动回灌、不移动 Broker offset；AI 网关调度与调用事实持久化 traceId。故障脚本覆盖应用 SIGKILL、Broker 重启/暂停、MySQL 暂停和重复投递探针；提交请求线程旧 Feign 触发和 `LEGACY_FEIGN` 状态已删除，仅保留读取同一 Outbox 的 `MQ_PRIMARY` 与 `FEIGN_RELAY`。
- 验收：后端全量 605 项测试中 588 项通过、17 项外部门禁跳过、零失败，20 项 Maven Reactor 全部构建成功，前端生产构建通过。五条业务消息协议均通过真实 RocketMQ 5.5.0 门禁；submission、review、ranking、suggestion、evaluation 与 ai-gateway 使用全新 MySQL 8 库真实启动并完成 V5/V7/V5/V4/V10/V9 迁移。ai-review 真实运维端点返回运行中 consumer，并从 `%DLQ%lm-dev%cg-ai-review-task-v1` 读取到 2 条历史死信而未自动消费。临时验收库已删除。

### [x] C2 三级缓存开发

- 目标：让公开题库与当前排行真实使用 HTTP、Caffeine、Redis 三级缓存，并在写入、消息丢失和 Redis 故障时按 C1 设计收敛。
- 入口：`/api/public/problems`、`/api/rankings/problems/{problemId}` 与题库管理、排行重建写入入口。
- 主流程：公开 GET 先处理 HTTP 条件缓存，服务内按 Caffeine、独立业务 Redis、MySQL 命中与回填；写事务同时记录 Outbox，提交后幂等推进区域版本并广播 L2 失效。
- 完成标准：三层命中与回填、`304`、空值、版本并发、Outbox 重试、Pub/Sub 丢失对账、Redis 降级和 Token 黑名单物理隔离均有自动化证据，目标服务可按真实配置启动。
- 修改范围：新增 `common-cache`，调整 problem-service、ranking-service、Docker Compose、Flyway、目标服务配置、测试和相关文档。
- 非目标：不缓存权限、运行中任务、上传状态、AI 内容、自由文本搜索和全局管理聚合，不引入布隆过滤器或启动全量预热。
- 完成摘要：新增 `common-cache`，题库筛选、受控分页、已发布详情和单题当前排行完成三级命中与回填；写事务通过本地 Outbox 推进区域版本并广播失效，Redis 不可用时回源并收紧到五秒本地缓存。
- 验收：公共缓存 21 项测试、problem-service 42 项测试、ranking-service 9 项测试通过；真实 Redis/MySQL 协议、两项 Flyway 迁移、`200/304`、版本化 Key、Redis 停机降级与恢复代际切换通过。

### [x] S12 有依据的 AI 论文建议闭环

- 完成摘要：每个已完成评审的论文版本均可由用户手动生成一份或多份建议；新增不可变的 `PAPER_PARSE_V1`、`EVIDENCE_REVIEW_V2`、`GROUNDED_SUGGESTION_V2` 和独立 `knowledge-retrieval-service`，建议同时锁定题面、论文页码、评审发现与知识引用，V1 数据和流程未被覆盖。
- 运行边界：正式建议固定使用 `VECTOR_RAG_V1`；`AI_DIRECTORY_V1` 与 `HYBRID_RETRIEVAL_V1` 已实现为实验分支，未通过既定固定对比门槛前不得切入正式建议。
- 验收：后端 Reactor 共 504 项测试，495 项通过、9 项外部条件测试按门禁跳过，零失败；前端生产构建通过；真实 MySQL 验证 review V1→V4、suggestion V1→V2 迁移及新旧版本共存。
- 设计：[有依据的论文建议链路](docs/project/02-架构设计/有依据的论文建议链路.md)。
- 归档：[S12 有依据的 AI 论文建议闭环](docs/project/02-架构设计/AI已完成阶段归档.md#s12-有依据的-ai-论文建议闭环)。

### [x] M1 管理端体验重构

- 完成摘要：管理端已按概览、访问控制、内容中心、业务运营和 AI 中枢五个工作域重组，完成侧栏与配色、分页授权、赛事编辑、题目与 PDF 预览、排行和 AI 可视化、版本目录、真实模型统计及全局分析视图；AI 客服限定在登录用户的普通业务布局，不进入管理端、认证页和异常页。
- 验收：后端目标模块 130 项测试通过，前端生产构建与桌面/窄屏真实浏览器关键路径通过，PDF、New-API 模型目录、AI 分页聚合和全局排行均使用真实接口验证。
- 归档：[管理端体验设计与验收](docs/project/03-微服务设计/admin-service/管理端体验设计.md)。

### [x] U1 论文 PDF 分片断点续传

- 完成摘要：submission-service 与队伍详情页已完成 20MB 内论文 PDF 的分片上传、断点续传、幂等合并、提交版本创建和评审触发，并已通过阶段分支合入 `dev`。
- 归档：[论文提交设计](docs/project/03-微服务设计/submission-service/论文提交/README.md)。

## 远期条件任务

### [ ] REST `/v1`、`/v2` 版本化

- 触发：实际出现无法兼容演进的外部 API 变更，并且能够列出真实调用方与迁移时点。
- 当前：Gateway、Controller、Feign、OpenAPI 和活跃前端调用已完成触发盘点，没有真实外部不兼容需求，因此按验收要求保持未开始。
- 边界：不使用 REST 版本代替 `workflowVersion`；触发后才定义双路由、弃用观测和下线周期。
- 证据：[REST API 版本化触发评估](docs/project/02-架构设计/REST%20API版本化触发评估.md)。

## 远期触发条件

生产版本切换与 REST API 版本化必须分别满足对应依赖和真实需求。以下能力暂不拆实现任务：

- 独立知识检索服务已由 AI 客服和论文建议两个真实消费者触发，纳入 S12 实现；后续在线知识管理和独立扩缩容在真实运维需求出现后再拆任务。
- 单调度器成为可测瓶颈或必须部署多个调度实例后，评估分布式 AI 调度。
- new-api 无法满足渠道治理且出现明确额外业务需求后，评估 LeetModel 多账号资源池。
- 指标具备可信真值且版本选择指数稳定运行后，才讨论自动推荐生产版本；不得自动激活。
- VIP 高级模型分层属于后续候选能力。进入设计前必须先确认普通与 VIP 在评审、建议、客服三个功能上的模型选择权、配额与计费、降级规则、运行快照和越权防护；未形成统一模型路由契约前不在各业务前端分别硬编码模型。

## 执行规则

1. 每次只选择一个编号任务卡，不直接领取整个阶段。
2. 默认由用户确认任务范围；托管模式下 Agent 按依赖自动领取并在完成后报告代码、测试、文档和未解决风险。
3. 任务依赖未满足时不得用临时硬编码绕过，应明确标记阻塞。
4. 新发现的问题若不阻断当前闭环，只记录到后续任务，不扩大当前任务。
5. 已完成阶段只在本文件保留摘要；关键决策与验收依据归档到对应 docs 文档。当前可执行任务卡为 MQ6，后续任务按依赖串行领取。
