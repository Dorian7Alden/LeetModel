## AI 版本标识

> 设计状态：已确认。五类核心标识分别演进、分别展示，任何 `/v1`、`/v2` 路径都不能代替业务工作流版本。已发布的 AI 功能不允许被原地覆盖；每次功能调整都必须通过新版本表达。

### 统一定义

| 契约字段 | 页面展示名 | 所有者 | 用途 | 创建后不可变范围 |
|----------|------------|--------|------|------------------|
| `apiVersion` | 接口版本 | 提供该 HTTP/RPC 契约的服务 | 标识请求、响应、错误和兼容策略 | 路径或媒体类型、字段语义、必填性、错误契约和兼容承诺 |
| `workflowVersion` | 工作流版本 | 执行该 AI 功能的业务服务 | 标识一套完整业务步骤、依赖、程序规则和输出语义 | 功能编码、步骤图、依赖、确定性处理规则和业务输出契约 |
| `promptVersion` | 提示词版本 | 拥有对应 Prompt 的 AI 业务服务 | 标识某工作流步骤使用的不可变 Prompt 内容 | Prompt 原文或模板、变量契约、适用步骤、内容哈希和安全约束 |
| `modelExecutionConfigVersion` | 模型执行配置版本 | `ai-gateway-service` | 把一个或多个逻辑步骤绑定到精确模型、参数和能力要求 | 逻辑模型、new-api 模型名、供应商可核对版本、采样参数、能力要求及步骤绑定 |
| `ragIndexVersion` | RAG 索引版本 | 当前构建发布仍为 `ai-assistant-service`；`knowledge-retrieval-service` 可锁定查询 | 标识一套可查询的 RAG 索引快照 | 知识源集合、内容版本、切分规则、Embedding 配置、索引 schema 和构建结果摘要 |

“所有者”负责生成标识、保证唯一性、保存不可变定义并提供查询；调用方只保存引用和运行快照，不复制所有者的版本表。

### 标识格式与展示

- 标识是机器稳定键，采用带领域前缀的可读编码或不可变 ID，例如 `REVIEW_V1`、`PROMPT_REVIEW_SUMMARY_2026_01`、`MODEL_CFG_REVIEW_0001`、`RAG_MATH_2026_08_001`。标识发布后不可复用。
- 页面必须同时展示“展示名称 + 标识”。展示名称可以是“基础评审”“低成本模型组合”，仅用于阅读；修改展示名称不能改变标识或历史运行。
- DTO、表字段和事件使用上表完整字段名。上下文已经明确且属于内部表时可以使用对应外键 ID，但 API 边界不得使用含糊的 `version`、`v1` 或 `modelVersion` 代替。
- OpenAPI 文档地址中的 `/v3/api-docs` 是 springdoc 的文档格式路径，不是 LeetModel 业务 API 版本；new-api 的 `/v1/chat/completions` 是上游协议版本，也不是 LeetModel 工作流版本。

### 变更判定

| 发生的变化 | 新建哪个版本 | 不应连带新建 |
|------------|--------------|--------------|
| 请求字段、响应语义或错误兼容边界改变 | `apiVersion` | 不自动新建工作流版本 |
| 工作流步骤、分支、依赖、确定性处理或业务输出语义改变 | `workflowVersion` | 不因单纯换模型而新建 |
| Prompt 模板正文或变量契约改变 | `promptVersion` | 不自动新建 API 或工作流版本 |
| 精确模型、模型参数、能力要求或步骤绑定改变 | `modelExecutionConfigVersion` | 不修改 Prompt 和工作流版本 |
| 知识内容、切分、Embedding 配置或索引 schema 改变并重建 | `ragIndexVersion` | 不把 RAG V1/V2 架构名称当索引版本 |

一次运行可以同时锁定五类标识。某项不适用时显式为空，不用另一类版本填充。例如无 RAG 的论文评审没有 `ragIndexVersion`，但仍应锁定工作流、Prompt 和模型执行配置版本。

### 创建、激活与历史

1. 草稿定义可以编辑；一旦发布并被任务引用，其不可变范围永久冻结。修改定义必须创建新标识。
2. “默认”“启用”“停用”是指向不可变版本的可变状态。切换默认值只影响新任务，不回写排队中、执行中或历史任务。
3. 业务任务和实验运行在创建时保存适用版本引用；进入执行前再校验引用仍可执行，但不得静默替换。
4. 调用审计保存实际执行的版本快照和 `callId`。评价比较必须至少锁定 `workflowVersion`、`modelExecutionConfigVersion`，并按功能需要锁定 `promptVersion` 与 `ragIndexVersion`。
5. 版本删除只允许发生在从未发布、从未引用的草稿；已引用版本只能停用。
6. 版本升级可以复用上一版代码或重新实现，但不得修改已发布版本的分支、Prompt 快照、输出契约、默认依赖或历史数据来伪装升级。

### 各层使用边界

- REST 路由、客户端兼容和 OpenAPI 描述只使用 `apiVersion`。
- AI 业务任务、结果、实验候选项和页面的“功能版本”使用 `workflowVersion`，不得填写 `/v1`、`/v2`。
- Prompt 调试和审计使用 `promptVersion`；不得把一段 Prompt 文本或文件路径当版本标识。
- 业务服务引用 `modelExecutionConfigVersion`，由 AI 网关解析并校验不可变配置；new-api 的渠道 ID 和模型别名不直接充当该版本。
- 知识检索运行和评价样本使用 `ragIndexVersion`；“向量 RAG V1”“AI 目录导航 V2”是架构代际名称，不是索引快照标识。迁移后由 knowledge-retrieval-service 保存不可变定义，调用方只保存引用和运行快照。

### 领域子工作流版本

PDF 解析与知识检索都是可独立演进、可被上层工作流引用的领域子工作流：

| 引用字段 | 所有者 | 语义 |
|----------|--------|------|
| `paperParsingWorkflowVersion` | `ai-review-service` | 锁定 PDF 解析步骤、工具、质量门和产物契约，例如 `PAPER_PARSE_V1` |
| `retrievalWorkflowVersion` | `knowledge-retrieval-service` | 锁定检索分支、排序、裁剪、适用性与失败规则，例如 `VECTOR_RAG_V1` |

这两个字段是对 `workflowVersion` 类型在特定领域中的明确命名，不是新的通用版本类型。上层评审或建议工作流必须在任务创建时保存它们的引用，不能在执行时读取“当前默认”后静默漂移。

### 功能与版本发现契约

AI 功能 owner 通过内部只读接口返回 `AiFeatureDefinitionDTO`，评价平台和管理端只消费该目录，不维护第二份版本枚举。

| 字段 | 约束 |
|---|---|
| `featureCode` | 全局稳定的大写业务编码，例如 `REVIEW`、`ASSISTANT`、`SUGGESTION` |
| `name` | 面向管理员的功能展示名，不参与运行寻址 |
| `ownerService` | 唯一业务规则与版本目录所有者 |
| `supportedDatasetTypes` | owner 能接受的样本 Payload 类型编码 |
| `supportedMetricCodes` | owner 输出可支持的原始指标编码，不代表质量结论 |
| `workflowVersions` | owner 的完整已发布版本集合；包含启用和禁用版本以解释历史 |

每个 `AiWorkflowVersionDTO` 必须返回 `workflowVersion`、展示名称、`status`、`inputSchema`、`outputSchema` 和 `compatibility`。schema 字段是稳定的契约编码，不内嵌随意变动的自然语言结构；详细字段以 owner 的 DTO/OpenAPI 为准。`compatibility` 说明该版本对历史输入输出的读取承诺。

版本状态只允许以下语义：

- `ENABLED`：可创建新正式任务和新实验。
- `DISABLED`：禁止新建任务或实验，但目录仍返回该版本，历史任务保存的版本编码和结果快照继续可读。

管理端创建实验时只允许从 `ENABLED` 版本下拉列表选择。服务端仍必须重新向 owner 校验状态，不能信任页面传值。当前 REVIEW 目录由 ai-review-service 提供，admin-service 只做代理；ASSISTANT 和 SUGGESTION 在隔离实验能力完成前不发布到评价平台目录。

当前不同 AI 功能的版本完整度不同，以 [AI功能版本现状.md](AI功能版本现状.md) 和对应服务实现为准。迁移期间不得把现有 `workflowVersion` 改名后复用为其他版本，也不得根据 `/v1`、模型名或当前默认值反推历史任务缺失的版本。

### 工具集版本扩展

受控工具调用增加第六类运行标识 `toolsetVersion`，已于 2026-08-31 随 S11 落地到客服工作流目录、不可变生产配置、消息运行快照和工具调用审计。

`toolsetVersion` 由使用工具的 AI 业务服务拥有，用于锁定一次运行可以暴露的工具名称、描述、输入 Schema、单工具版本、终止行为、次数限制和权限要求。示例标识为 `ASSISTANT_TOOLSET_0001`。

工具集变化不自动改变模型、Prompt 或 RAG 索引。新增工具、删除工具、字段语义变化或终止行为变化时创建新的 `toolsetVersion`。工作流决定何时调用工具，因此工作流分支和调用顺序变化仍创建新的 `workflowVersion`。

正式运行把 `toolsetVersion` 与现有五类适用标识一起保存；工具版使用 `ASSISTANT_TOOLSET_0001`，无需工具的旧工作流使用空值表达不适用。历史消息只能读取自身快照，不能根据当前注册表反推工具集。
