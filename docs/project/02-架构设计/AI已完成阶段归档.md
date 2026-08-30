## AI 已完成阶段归档

本文归档已经完成的 AI 架构与实现阶段，只保留后续设计、排障和面试讲解仍需引用的决策、边界及验收证据。执行中的任务与未完成路线仍以根目录 `TODO.md` 为准。

### 正式设计校准

设计校准阶段统一解决了实现与旧文档的冲突：new-api 是供应商渠道、密钥、模型映射、渠道重试和额度治理的唯一所有者；LeetModel AI 网关只拥有内部契约、业务上下文、调度与调用事实。向量检索是当前 RAG V1，AI 目录导航是远期 RAG V2。评价体系严格区分运行、稳定性、具备证据的质量指标和版本选择指数。API、工作流、Prompt、模型执行配置和 RAG 索引使用五种独立版本标识。

权威设计见 [AI文档与代码冲突清单.md](AI文档与代码冲突清单.md)、[AI系统分层.md](AI系统分层.md)、[AI版本标识.md](AI版本标识.md)、[RAG知识库.md](RAG知识库.md) 和 [new-api第三方网关集成.md](new-api第三方网关集成.md)。

### S0 LangChain4j 兼容性

项目保留 LangChain4j `0.34.0`，不为尚未使用的高层 RAG API整体升级。生产向量链使用项目内适配器和 Elasticsearch 客户端，自动化测试使用确定性内存实现。版本矩阵、编译实验及升级触发条件见 [LangChain4j兼容性核验.md](LangChain4j兼容性核验.md)。

### S1 new-api Chat 主链

Chat 唯一生产链路为 `业务服务 → common-ai → ai-gateway-service → new-api`。业务服务不保存 Relay Token 或供应商密钥；旧 DeepSeek/Kimi 官方直连、相关配置和隐式回退均已删除。客服文本与论文评审多模态完成真实调用，模型、usage 和调用关联可得，日志不记录正文。发布回退以整个阶段的 Git 回滚为边界，不在运行时保留第二条供应商链路。

协议、模型差异、职责和错误边界见 [new-api第三方网关集成.md](new-api第三方网关集成.md)、[ai-gateway-service/11-供应商协议.md](../03-微服务设计/ai-gateway-service/11-供应商协议.md) 与 [ai-gateway-service/new-api黑盒核验.md](../03-微服务设计/ai-gateway-service/new-api黑盒核验.md)。

### S2 调用计量与成本

AI 网关拥有单次调用事实，业务方通过 AiCallContext 提供来源、业务任务和工作流关联。审计区分输入、输出、推理、缓存和总 Token，保存实际或估算费用及其完整性，拆分排队、执行和总耗时；缺失 usage 或费用必须显式未知，不能用零伪装。费用补全按调用幂等且不改写已确认实际费用。

数据模型、价格口径、业务上下文和管理查询见 [ai-gateway-service/14-调用记录与状态.md](../03-微服务设计/ai-gateway-service/14-调用记录与状态.md)、[15-价格与计量.md](../03-微服务设计/ai-gateway-service/15-价格与计量.md)、[18-管理查询.md](../03-微服务设计/ai-gateway-service/18-管理查询.md) 与 [24-业务调用上下文.md](../03-微服务设计/ai-gateway-service/24-业务调用上下文.md)。

### S3 Embedding 统一调用

Embedding 与 Chat 共用 `common-ai → ai-gateway-service → new-api` 治理链。`RAG_V1` 固定使用 `qwen3.7-text-embedding` 和 1024 维向量，支持单条与批量输入、数量和长度上限、维度漂移拒绝、统一错误与调用审计。真实中文冒烟返回稳定维度和 usage，输入原文与向量不进入审计或日志。

契约、限制和验收边界见 [ai-gateway-service/25-Embedding统一调用.md](../03-微服务设计/ai-gateway-service/25-Embedding统一调用.md) 与 [common-ai/AI调用客户端.md](../03-微服务设计/common/common-ai/AI调用客户端.md)。

### S4 客服 RAG V1

RAG V1 归 `ai-assistant-service`，知识源只选择 `rag_kb/数学建模` 中排除 README 的受控 Markdown。流水线使用确定性选择、清洗切分、稳定内容版本、1024 维 Embedding、物理 Elasticsearch 索引和原子读别名。客服检索按 Top K、阈值与 Token 预算注入来源化上下文；常规检索失败可安全降级，无界面或业务写操作。正式索引不提供破坏性清理命令，索引回滚通过别名切换完成。

知识源、索引、召回、审计和演进边界见 [RAG知识库.md](RAG知识库.md) 与 [ai-assistant-service/README.md](../03-微服务设计/ai-assistant-service/README.md)。

### S5 AI 网关优先级调度

AI 网关形成单实例、持久化、确定性公平的 Chat/Embedding 调度链。可信业务场景决定 P0 至 P4，调用方声明不能越权；调度使用加权公平、老化和 P0 保留容量，后台任务不能阻断客服且不会永久饥饿。任务采用调用方幂等键、deadline、租约和 attempt 状态；只有可证明尚未发送的任务可安全恢复，`DISPATCHING` 或 `ACKNOWLEDGED` 后失联进入 UNKNOWN，禁止盲目重发和重复扣费。管理员只读查询不返回 Prompt、回答、向量、幂等键或请求哈希。

队列、恢复、计量和运维入口见 [ai-gateway-service/05-原子调用任务.md](../03-微服务设计/ai-gateway-service/05-原子调用任务.md)、[06-任务队列与调度.md](../03-微服务设计/ai-gateway-service/06-任务队列与调度.md)、[10-幂等重试与故障恢复.md](../03-微服务设计/ai-gateway-service/10-幂等重试与故障恢复.md) 与 [18-管理查询.md](../03-微服务设计/ai-gateway-service/18-管理查询.md)。多实例全局许可仍是明确非目标。

### S6 AI 功能与版本契约

REVIEW 与 ASSISTANT 发布统一、可发现的功能和工作流版本目录，以及不写正式业务结果的隔离实验入口。不可变 modelExecutionConfigVersion 锁定物理模型、参数、Prompt/工作流适用范围、thinking 与输出格式；任务保存完整执行快照，因此模型别名变化不改变历史含义。REVIEW 使用通用 V2 实验契约，旧专用入口仅作兼容；ASSISTANT 提供无 RAG 与 RAG V1 两个版本，RAG 实验必须访问并校验指定物理索引，不能读取当前别名或降级。SUGGESTION 在专用隔离入口落地前不进入可评价集合。

功能准入、五种版本、模型配置和隔离副作用边界见 [AI功能版本现状.md](AI功能版本现状.md)、[AI版本标识.md](AI版本标识.md)、[AI隔离实验契约.md](AI隔离实验契约.md) 与 [ai-gateway-service/04-模型执行配置与能力校验.md](../03-微服务设计/ai-gateway-service/04-模型执行配置与能力校验.md)。阶段验收为后端 17 模块 327 项测试零失败、前端生产构建通过，9 项外部条件测试按门控跳过。

### S7 通用评价平台

评价服务从 REVIEW 专用实现迁移为 REVIEW/ASSISTANT 通用平台，拥有版本化数据集、指标注册表、隔离 Runner、规模预估、P3 幂等运行槽位和暂停、恢复、取消、失败项重试状态机。任务锁定功能、工作流、模型执行配置、数据集和可选 RAG 索引版本；原始运行事实区分成功、稳定性、具备证据的质量指标与缺失，不使用零伪装未知值。只有同功能、同数据集与指标口径的候选可以比较，旧 REVIEW 接口按明确退出门槛保留兼容。

领域模型、状态机、指标公式、迁移与验收见 [通用评价](../03-微服务设计/ai-evaluation-service/通用评价/README.md)、[评价指标与版本选择指数.md](../03-微服务设计/ai-evaluation-service/评价指标与版本选择指数.md) 和 [迁移回归与兼容退出.md](../03-微服务设计/ai-evaluation-service/通用评价/迁移回归与兼容退出.md)。阶段验收为后端 17 模块 373 项测试零失败、前端生产构建及真实 MySQL 8.0.33 V1 至 V6 迁移通过，9 项外部 smoke 按门控跳过。

### S8 权重与管理端闭环

评价服务拥有不可变归一化配置和权重方案，严格校验指标口径、单位、方向、缺失策略及权重精确合计 100%。任务完成时保存原始指标、归一化结果、权重、贡献和版本选择指数快照；另一方案重算只追加新的 `scoreResultVersion`，不覆盖历史或原始事实。admin-service 只负责管理员鉴权、操作者注入和下游转发，不建立评价持久化；管理页使用受控版本选择、服务端成本预估、高成本双重确认、任务控制、原始指标、贡献及 callId 追踪，明确选择指数不是准确率。

公式、所有权和不可变规则见 [权重与选择指数](../03-微服务设计/ai-evaluation-service/权重与选择指数/README.md)，管理边界与真实验收见 [AI 测试控制](../03-微服务设计/admin-service/AI测试控制/README.md) 和 [端到端验收.md](../03-微服务设计/admin-service/AI测试控制/端到端验收.md)。真实 REVIEW 与 ASSISTANT_NO_RAG 实验验证暂停、恢复、失败项重试、两版权重重算、权限、调用一致性和 P0 客服保留；阶段末评价模块 85 项、直接依赖合计 94 项测试及前端生产构建通过。固定 RAG 索引尚未构建，因此 ASSISTANT_RAG_V1 只完成显式失败与重试证据，不伪装成功。

### S9 RAG V2 受控目录设计

RAG V2 仅完成设计，不包含运行时代码。manifest 对 `rag_kb/数学建模/` 的 V1 受控 Markdown 保存目录、文档名、tags、summary、结构版本、内容源版本和路径白名单；缺失 summary 整版拒绝发布。当前 73 篇文档的完整紧凑 manifest 实测为 29,145 字节，`cl100k_base` 10,848 Token、`o200k_base` 8,396 Token。选择器最多返回 4 个 manifest 精确成员，服务端再执行规范化路径校验、受限加载和确定性裁剪；失败或超时始终保留 V1。

固定实验锁定数据集、索引、目录、Embedding、Prompt、模型配置、组合策略和指标口径，至少覆盖 40 题、每候选每题 3 次，并要求双人独立标注和第三人裁决。没有真值或人工证据不得宣称准确率。只有答案支持和召回显著提升，同时满足精度、安全、失败率、P95 延迟、Token 与费用门槛后，才允许另立实现任务；在线文件管理、独立 RAG 服务、任意文件访问和自动生产激活仍排除。完整设计见 [RAG 目录导航 V2](../03-微服务设计/ai-assistant-service/RAG目录导航V2/README.md)。

### 验收环境与密钥边界

本地开发 new-api 的测试 Relay Token 位于仓库外 `~/Desktop/new-api-test-key.txt`。该路径用于跨会话定位测试凭据，但文件内容不得复制到仓库、日志、测试夹具或提交记录；测试时只临时注入进程环境。2026-08-28 已核验本地 new-api 可用 DeepSeek、Kimi 和 `qwen3.7-text-embedding`，这是当日环境事实，不是永久模型承诺，每次真实冒烟前仍需查询模型目录并执行最小请求。

阶段 S1 至 S6 的真实冒烟均遵守此边界。详细测试策略见 [ai-gateway-service/22-测试与验收.md](../03-微服务设计/ai-gateway-service/22-测试与验收.md)。
