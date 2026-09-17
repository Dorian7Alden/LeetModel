# Embedding 统一调用

> S3 实现基线。业务服务只能通过 `common-ai` 调用 `ai-gateway-service`，不得持有 new-api 地址、渠道名或 Relay Token。

## common-ai 契约

- `AiEmbeddingRequest.logicalModel` 是网关管理的稳定逻辑模型名，不是供应商模型名。
- 单条输入使用与批量相同的 `inputs` 数组结构；公共契约最多 128 条、每条最多 32768 个字符，网关按逻辑模型继续施加更严格的批量和总量限制。
- `AiCallContext` 必填。RAG 建库使用 `RAG/INDEX_DOCUMENTS`，在线查询使用 `RAG/RETRIEVE_CONTEXT`，网关只把业务 ID 当作不透明关联键。
- `AiEmbeddingResponse` 返回 LeetModel `callId`、逻辑模型、实际模型、统一维度、按输入索引排列的向量、usage 和费用。
- 向量值必须是有限浮点数；同一响应的向量维度必须一致，索引不得重复。Token 或费用不可得时继续使用统一的 UNKNOWN 完整性语义。

请求与响应均不包含 Base URL、渠道标识或密钥。原文和向量只在调用链中传输，不进入审计日志；审计仅保存条数、维度、用量、费用、耗时和调用标识。

`AiClient.embed` 固定调用 `/internal/ai/embeddings`。现有 Chat-only 实现继续保持函数式接口兼容，默认明确返回“不支持 Embedding”；正式 `HttpAiClient` 覆盖该方法，并把业务失败、空响应、HTTP 失败和连接/读取超时统一转换为 `AiClientException`，异常消息不拼接请求原文。

网关通过 `ai.gateway.embedding-models.<logicalModel>` 绑定 new-api 物理模型，并配置启用状态、预期维度、最大批量、单条字符上限和批次总字符上限。接口仅接受 `RAG/INDEX_DOCUMENTS` 与 `RAG/RETRIEVE_CONTEXT` 上下文；未知或停用模型、越界输入、响应条数/索引/维度异常均在网关边界明确拒绝。

new-api 适配固定调用 `/v1/embeddings`，请求使用数组 `input` 和 `encoding_format=float`。响应按 `data.index` 归一化，Embedding 协议中明确不存在的输出 Token 记为 0；上游未提供 usage 时整体保持 UNKNOWN。认证、限流、额度、超时、模型缺失和畸形响应沿用网关统一错误分类，不透明重试。

`ai-assistant-service` 的 `CommonAiEmbeddingModel` 实现 LangChain4j 0.34.0 `EmbeddingModel`，通过注入的 `AiClient` 批量调用并校验完整索引、维度和有限数值，再映射为 LangChain4j `Embedding` 与 `TokenUsage`。0.34.0 的 `Response` 没有任意 metadata 容器，因此 `callId` 保存在网关审计事实中供业务任务关联，不伪造到 LangChain4j 元数据；客服服务配置中不出现 new-api 地址或 Token。

Embedding 与 Chat 共用 `ai_call_log`。新增 `callType=EMBEDDING`、输入条数和向量维度，只保存上下文、模型、usage、费用、耗时、错误和调用 ID；输入原文与向量不得落库。

2026-08-28 的正式绑定与实测基线为：逻辑模型 `RAG_V1`、new-api 模型 `qwen3.7-text-embedding`、向量维度 1024。两条中文输入经完整内部链路返回 2 个等维向量和 53 个输入/总 Token，响应 callId 可在 calls 接口与 MySQL 审计行中追踪到同一条 `EMBEDDING` 事实。更换模型或维度必须更新不可变模型配置版本并重建对应 RAG 索引，不能静默沿用旧索引。
