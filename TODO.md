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

当前分支 `phase/knowledge-retrieval-optimization`，开启知识库检索精准度优化、成本控制与微服务解耦演进阶段。

## 当前任务

- [ ] 任务卡 1: 切片结构化与面包屑上下文增强（Breadcrumbs Injection）
  - 业务流程与职责：在分块处理类（`ChineseKnowledgeChunker` / `MarkdownKnowledgeLoader`）中，解析原子 Markdown 相对路径与 Frontmatter 标题，在每个切片正文前置插入标准化面包屑（如 `[目录: 数学建模 > 题型方法 > 优化模型] [文档: 线性规划]`），以零模型成本增强向量与 BM25 在短文本上的语义空间坐标。
  - 实施步骤：修改分块逻辑注入前缀；更新 `RagIdentityFactory` 保持哈希自洽；编写单测验证前缀生成与全量构建。

- [ ] 任务卡 2: 目录与标签 AI 智能选拔（`AI_DIRECTORY_V1`）落地与服务端 4 道防线
  - 业务流程与职责：以低价 Flash 级模型调度轻量 Manifest 执行代表性选拔，在服务端构筑绝对白名单校验、数量硬截断、防御性 JSON 解析与优雅降级 4 道防线，实现 2~4 篇原子文档整篇装配。

- [ ] 任务卡 3: 任务级语义缓存（Redis Semantic Cache）与分类前置过滤
  - 业务流程与职责：在 `knowledge-retrieval-service` 引入 Redis 任务向量相似度匹配（$\ge 0.95$ 直接复用选文结果，0ms 极速返回）与 `category` 前置过滤减枝。

- [ ] 任务卡 4: 知识库自包含迁移与存储解耦（`README.yaml` 解析器 + MinIO/MySQL 导入导出）
  - 业务流程与职责：实现 `README.yaml` 标准解析器，将 Markdown 事实源同步至 MinIO，建立 `lm_knowledge` 元数据表与 ZIP 一键导入导出闭环。

---

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
